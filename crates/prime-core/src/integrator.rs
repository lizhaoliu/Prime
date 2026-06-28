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
//! * **deterministic** — each pixel seeds its own RNG from `(seed, x, y)`, so a
//!   render is byte-for-byte reproducible regardless of thread scheduling.

use crate::camera::Camera;
use crate::color::Tonemap;
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
                let mut rng = pixel_rng(settings.seed, x, y);
                let mut acc = Color::ZERO;
                for _ in 0..settings.samples_per_pixel {
                    // Jittered sample within the pixel; flip y so row 0 is the
                    // top of the image.
                    let s = (x as Float + rng.gen::<Float>()) / width as Float;
                    let t = (height as Float - 1.0 - y as Float + rng.gen::<Float>())
                        / height as Float;
                    let ray = camera.get_ray(s, t, &mut rng);
                    acc += radiance(scene, ray, settings.max_depth, &mut rng);
                }
                *pixel = acc * inv_spp;
            }
            on_row_done();
        });

    fb
}

/// Estimate the radiance arriving along `ray` via iterative path tracing.
fn radiance<R: Rng + ?Sized>(
    scene: &Scene,
    mut ray: Ray,
    max_depth: usize,
    rng: &mut R,
) -> Color {
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

/// Deterministic per-pixel RNG derived from the base seed and pixel coordinates
/// via a SplitMix64-style hash, so threads never share or contend on state.
#[inline]
fn pixel_rng(seed: u64, x: usize, y: usize) -> SmallRng {
    let mut z = seed
        .wrapping_add((y as u64).wrapping_mul(0x9E37_79B9_7F4A_7C15))
        .wrapping_add((x as u64).wrapping_mul(0xD1B5_4A32_D192_ED03))
        .wrapping_add(0x1234_5678_9ABC_DEF0);
    z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    z ^= z >> 31;
    SmallRng::seed_from_u64(z)
}

/// Convenience: render and resolve to interleaved RGB8 bytes.
pub fn render_to_srgb<F>(scene: &Scene, settings: &RenderSettings, on_row_done: F) -> Vec<u8>
where
    F: Fn() + Sync + Send,
{
    let fb = render(scene, settings, on_row_done);
    fb.to_srgb_bytes(settings.tonemap, settings.gamma)
}
