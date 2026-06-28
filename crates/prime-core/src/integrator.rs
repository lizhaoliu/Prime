//! The path-tracing integrator.
//!
//! Compared to the legacy `PathTracer` (recursive, with intertwined and partly
//! buggy direct-illumination logic, and a fresh `ExecutorService` leaked on
//! every render), this integrator is:
//!
//! * **iterative** — radiance and throughput are carried in locals, so depth is
//!   bounded by a loop, not the call stack;
//! * **data-parallel** — rows are rendered concurrently with Rayon over the
//!   global pool, nothing is leaked;
//! * **deterministic** — each pixel seeds its own RNG from `(seed, x, y, salt)`,
//!   so a render is byte-for-byte reproducible regardless of thread scheduling.
//!
//! Two front-ends share the same machinery:
//! [`render`] does a one-shot batch render; [`ProgressiveRenderer`] accumulates
//! samples pass-by-pass for interactive viewers (each pass uses a fresh sample
//! `salt`, so passes add genuinely new samples rather than repeating).

use crate::camera::{Camera, CameraConfig};
use crate::color::{self, Tonemap};
use crate::framebuffer::Framebuffer;
use crate::ray::Ray;
use crate::scene::Scene;
use crate::{Color, Float};
use rand::rngs::SmallRng;
use rand::{Rng, SeedableRng};
use rayon::prelude::*;

#[derive(Clone, Copy, Debug)]
pub struct RenderSettings {
    pub width: usize,
    pub height: usize,
    pub samples_per_pixel: usize,
    pub max_depth: usize,
    /// Base RNG seed; the same seed reproduces the same image.
    pub seed: u64,
    pub tonemap: Tonemap,
    pub gamma: Float,
}

impl Default for RenderSettings {
    fn default() -> Self {
        RenderSettings {
            width: 800,
            height: 450,
            samples_per_pixel: 64,
            max_depth: 32,
            seed: 0,
            tonemap: Tonemap::Clamp,
            gamma: 2.2,
        }
    }
}

/// Shadow-ray/self-intersection epsilon (in world units).
const T_MIN: Float = 1e-3;
/// Bounce after which Russian roulette path termination kicks in.
const RR_START_DEPTH: usize = 4;

/// Render `scene` into a fresh framebuffer. `on_row_done` is invoked once per
/// completed scanline (for progress reporting); pass `|| {}` to ignore it.
pub fn render<F>(scene: &Scene, settings: &RenderSettings, on_row_done: F) -> Framebuffer
where
    F: Fn() + Sync + Send,
{
    let mut fb = Framebuffer::new(settings.width, settings.height);
    let camera = Camera::new(&scene.camera, settings.width as Float / settings.height as Float);

    let width = settings.width;
    let height = settings.height;
    let inv_spp = 1.0 / settings.samples_per_pixel as Float;

    fb.pixels_mut()
        .par_chunks_mut(width)
        .enumerate()
        .for_each(|(y, row)| {
            for (x, pixel) in row.iter_mut().enumerate() {
                let mut rng = pixel_rng(settings.seed, x, y, 0);
                let acc = sample_pixel(
                    scene,
                    &camera,
                    x,
                    y,
                    width,
                    height,
                    settings.max_depth,
                    settings.samples_per_pixel,
                    &mut rng,
                );
                *pixel = acc * inv_spp;
            }
            on_row_done();
        });

    fb
}

/// Convenience: render and resolve to interleaved RGB8 bytes.
pub fn render_to_srgb<F>(scene: &Scene, settings: &RenderSettings, on_row_done: F) -> Vec<u8>
where
    F: Fn() + Sync + Send,
{
    let fb = render(scene, settings, on_row_done);
    fb.to_srgb_bytes(settings.tonemap, settings.gamma)
}

/// An accumulating renderer for interactive/progressive use.
///
/// Call [`ProgressiveRenderer::render_pass`] repeatedly; each call adds samples
/// to a running per-pixel sum. [`ProgressiveRenderer::to_srgb_bytes`] resolves
/// the current average at any time. To change the camera or resolution, build a
/// new renderer — construction is cheap (it only allocates the accumulation
/// buffer; the scene/BVH is borrowed at render time).
pub struct ProgressiveRenderer {
    width: usize,
    height: usize,
    max_depth: usize,
    seed: u64,
    camera: Camera,
    sum: Vec<Color>,
    samples: usize,
}

impl ProgressiveRenderer {
    pub fn new(
        camera_config: &CameraConfig,
        width: usize,
        height: usize,
        max_depth: usize,
        seed: u64,
    ) -> ProgressiveRenderer {
        let camera = Camera::new(camera_config, width as Float / height as Float);
        ProgressiveRenderer {
            width,
            height,
            max_depth,
            seed,
            camera,
            sum: vec![Color::ZERO; width * height],
            samples: 0,
        }
    }

    pub fn width(&self) -> usize {
        self.width
    }

    pub fn height(&self) -> usize {
        self.height
    }

    /// Total samples-per-pixel accumulated so far.
    pub fn samples(&self) -> usize {
        self.samples
    }

    /// Add `count` more samples per pixel to the accumulation buffer.
    pub fn render_pass(&mut self, scene: &Scene, count: usize) {
        let width = self.width;
        let height = self.height;
        let max_depth = self.max_depth;
        let seed = self.seed;
        let salt = self.samples as u64;
        let camera = &self.camera;

        self.sum
            .par_chunks_mut(width)
            .enumerate()
            .for_each(|(y, row)| {
                for (x, pixel) in row.iter_mut().enumerate() {
                    let mut rng = pixel_rng(seed, x, y, salt);
                    *pixel += sample_pixel(
                        scene, camera, x, y, width, height, max_depth, count, &mut rng,
                    );
                }
            });

        self.samples += count;
    }

    /// Resolve the current average to interleaved RGB8 bytes.
    pub fn to_srgb_bytes(&self, tonemap: Tonemap, gamma: Float) -> Vec<u8> {
        let inv = if self.samples > 0 {
            1.0 / self.samples as Float
        } else {
            0.0
        };
        let mut out = Vec::with_capacity(self.width * self.height * 3);
        for &c in &self.sum {
            out.extend_from_slice(&color::to_srgb8(c * inv, tonemap, gamma));
        }
        out
    }
}

/// Draw `count` jittered samples for pixel `(x, y)` and return their summed
/// radiance (the caller divides by the sample count). The y axis is flipped so
/// row 0 is the top of the image.
#[inline]
#[allow(clippy::too_many_arguments)]
fn sample_pixel<R: Rng + ?Sized>(
    scene: &Scene,
    camera: &Camera,
    x: usize,
    y: usize,
    width: usize,
    height: usize,
    max_depth: usize,
    count: usize,
    rng: &mut R,
) -> Color {
    let mut acc = Color::ZERO;
    for _ in 0..count {
        let s = (x as Float + rng.gen::<Float>()) / width as Float;
        let t = (height as Float - 1.0 - y as Float + rng.gen::<Float>()) / height as Float;
        let ray = camera.get_ray(s, t, rng);
        acc += radiance(scene, ray, max_depth, rng);
    }
    acc
}

/// Estimate the radiance arriving along `ray` via iterative path tracing.
fn radiance<R: Rng + ?Sized>(scene: &Scene, mut ray: Ray, max_depth: usize, rng: &mut R) -> Color {
    let mut radiance = Color::ZERO;
    let mut throughput = Color::ONE;

    for depth in 0..max_depth {
        let Some(hit) = scene.hit(&ray, T_MIN, Float::INFINITY) else {
            radiance += throughput * scene.background.sample(ray.dir);
            break;
        };

        let material = scene.material(hit.material);
        radiance += throughput * material.emitted();

        let Some(scatter) = material.scatter(&ray, &hit, rng) else {
            break; // absorbed (or a light, which only emits)
        };

        throughput = throughput * scatter.attenuation;
        ray = Ray::new(hit.p, scatter.direction);

        // Russian roulette: unbiasedly terminate dim paths once they have had a
        // chance to gather light.
        if depth >= RR_START_DEPTH {
            let survive = throughput.max_component().clamp(0.05, 0.95);
            if rng.gen::<Float>() > survive {
                break;
            }
            throughput = throughput / survive;
        }
    }

    radiance
}

/// Deterministic per-pixel RNG derived from the base seed, pixel coordinates,
/// and a `salt` (distinct per progressive pass) via a SplitMix64-style hash, so
/// threads never share or contend on state.
#[inline]
fn pixel_rng(seed: u64, x: usize, y: usize, salt: u64) -> SmallRng {
    let mut z = seed
        .wrapping_add((y as u64).wrapping_mul(0x9E37_79B9_7F4A_7C15))
        .wrapping_add((x as u64).wrapping_mul(0xD1B5_4A32_D192_ED03))
        .wrapping_add(salt.wrapping_mul(0x2545_F491_4F6C_DD1D))
        .wrapping_add(0x1234_5678_9ABC_DEF0);
    z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    z ^= z >> 31;
    SmallRng::seed_from_u64(z)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::demo;

    fn mean_luma(bytes: &[u8]) -> f64 {
        let sum: u64 = bytes.iter().map(|&b| b as u64).sum();
        sum as f64 / bytes.len() as f64
    }

    #[test]
    fn progressive_accumulation_advances_and_renders() {
        let scene = demo::cornell_box();
        let mut pr = ProgressiveRenderer::new(&scene.camera, 64, 64, 16, 0);
        assert_eq!(pr.samples(), 0);
        pr.render_pass(&scene, 4);
        pr.render_pass(&scene, 4);
        assert_eq!(pr.samples(), 8);
        let bytes = pr.to_srgb_bytes(Tonemap::Clamp, 2.2);
        assert_eq!(bytes.len(), 64 * 64 * 3);
        assert!(mean_luma(&bytes) > 1.0, "image should not be all black");
    }

    #[test]
    fn progressive_matches_batch_within_noise() {
        // Many 1-spp passes should converge to roughly the same image as a
        // single batch render at the same total spp (same seed scheme differs,
        // so compare means, not exact pixels).
        let scene = demo::spheres();
        let settings = RenderSettings {
            width: 48,
            height: 32,
            samples_per_pixel: 32,
            max_depth: 8,
            seed: 0,
            tonemap: Tonemap::Clamp,
            gamma: 2.2,
        };
        let batch = render_to_srgb(&scene, &settings, || {});

        let mut pr = ProgressiveRenderer::new(&scene.camera, 48, 32, 8, 0);
        for _ in 0..32 {
            pr.render_pass(&scene, 1);
        }
        let prog = pr.to_srgb_bytes(Tonemap::Clamp, 2.2);

        let d = (mean_luma(&batch) - mean_luma(&prog)).abs();
        assert!(d < 6.0, "mean luma differs too much: {d}");
    }
}
