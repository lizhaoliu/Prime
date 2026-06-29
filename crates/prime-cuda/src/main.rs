//! `prime-gpu` — experimental CUDA front-end for Prime.
//!
//! **Phase B:** uploads a scene's flattened BVH + primitives to the GPU and runs
//! iterative BVH traversal in a kernel for primary-ray visibility. The result is
//! a normal-visualization PNG, and — because the kernel mirrors the CPU
//! `Bvh::hit` exactly — the per-pixel hit distance is **validated against the CPU
//! renderer** (the reference). Later: the full path tracer on the GPU.
//!
//! Requires an NVIDIA GPU + CUDA toolkit (this crate is excluded from the
//! workspace). Build/run with the CUDA path set:
//!   CUDA_PATH=/usr/local/cuda cargo run --manifest-path crates/prime-cuda/Cargo.toml --release -- cornell

use std::path::{Path, PathBuf};

use anyhow::{bail, Context, Result};
use clap::Parser;
use cudarc::driver::{CudaContext, LaunchConfig, PushKernelArg};
use cudarc::nvrtc::compile_ptx;

use prime_core::aabb::Aabb;
use prime_core::camera::CameraConfig;
use prime_core::color::{to_srgb8, Tonemap};
use prime_core::geometry::Primitive;
use prime_core::material::Material;
use prime_core::math::Vec3;
use prime_core::ray::Ray;
use prime_core::scene::{Background, Scene};
use prime_core::{demo, obj, Color, Float};

const TRACE_SRC: &str = include_str!("kernels/trace.cu");

#[derive(Parser, Debug)]
#[command(
    name = "prime-gpu",
    version,
    about = "Experimental CUDA renderer for Prime"
)]
struct Args {
    /// Scene: a built-in name (cornell, showcase, spheres, rtweekend, studio,
    /// sky) or a path to a `.obj` mesh.
    #[arg(default_value = "cornell")]
    scene: String,
    /// Output PNG path.
    #[arg(short, long, default_value = "out/gpu.png")]
    output: PathBuf,
    /// Image width in pixels.
    #[arg(short, long, default_value_t = 800)]
    width: usize,
    /// Image height in pixels.
    #[arg(long, default_value_t = 600)]
    height: usize,
}

fn load_scene(name: &str) -> Result<Scene> {
    Ok(match name {
        "cornell" => demo::cornell_box(),
        "showcase" => demo::showcase(),
        "spheres" => demo::spheres(),
        "rtweekend" => demo::rtweekend(),
        "studio" => demo::studio(),
        "sky" => demo::sky(),
        other if other.ends_with(".obj") => load_obj(Path::new(other))?,
        other => {
            bail!("unknown scene '{other}' (try cornell/showcase/spheres/rtweekend or a .obj)")
        }
    })
}

/// Load a bare OBJ mesh and frame a camera around its bounds.
fn load_obj(path: &Path) -> Result<Scene> {
    let tris = obj::load(path, 0, obj::Transform::default())
        .with_context(|| format!("loading {}", path.display()))?;
    let prims: Vec<Primitive> = tris.into_iter().map(Primitive::from).collect();
    let bounds = prims.iter().fold(Aabb::EMPTY, |b, p| b.union(p.aabb()));
    let center = bounds.centroid();
    let radius = ((bounds.max - bounds.min).length() * 0.5).max(1e-3);
    let camera = CameraConfig {
        look_from: center + Vec3::new(0.0, radius * 0.25, radius * 2.2),
        look_at: center,
        vup: Vec3::new(0.0, 1.0, 0.0),
        vfov: 35.0,
        aperture: 0.0,
        focus_dist: None,
    };
    let materials = vec![Material::Lambertian {
        albedo: Color::splat(0.7).into(),
        normal: None,
    }];
    Ok(Scene::new(materials, prims, camera, Background::default()))
}

/// A pinhole camera frame matching `prime_core::Camera` (for `aperture = 0`),
/// packed as `[origin, lower_left, horizontal, vertical]`. The focus distance
/// cancels out of the normalized ray direction, so it is omitted here.
struct CamBasis {
    origin: Vec3,
    lower_left: Vec3,
    horizontal: Vec3,
    vertical: Vec3,
}

impl CamBasis {
    fn new(cfg: &CameraConfig, aspect: Float) -> CamBasis {
        let theta = cfg.vfov.to_radians();
        let h = (theta / 2.0).tan();
        let viewport_h = 2.0 * h;
        let viewport_w = aspect * viewport_h;
        let w = (cfg.look_from - cfg.look_at).normalize();
        let u = cfg.vup.cross(w).normalize();
        let v = w.cross(u);
        let horizontal = u * viewport_w;
        let vertical = v * viewport_h;
        let lower_left = cfg.look_from - horizontal * 0.5 - vertical * 0.5 - w;
        CamBasis {
            origin: cfg.look_from,
            lower_left,
            horizontal,
            vertical,
        }
    }

    fn pack(&self) -> Vec<f32> {
        let g = |p: Vec3| [p.x, p.y, p.z];
        [
            g(self.origin),
            g(self.lower_left),
            g(self.horizontal),
            g(self.vertical),
        ]
        .concat()
    }

    /// The ray through pixel `(x, y)` — identical to the kernel's ray gen.
    fn ray(&self, x: usize, y: usize, w: usize, h: usize) -> Ray {
        let s = (x as Float + 0.5) / w as Float;
        let t = (h as Float - 1.0 - y as Float + 0.5) / h as Float;
        let dir =
            (self.lower_left + self.horizontal * s + self.vertical * t - self.origin).normalize();
        Ray::new(self.origin, dir)
    }
}

fn main() -> Result<()> {
    let args = Args::parse();
    let (w, h) = (args.width, args.height);
    let aspect = w as Float / h as Float;

    let scene =
        load_scene(&args.scene).with_context(|| format!("loading scene '{}'", args.scene))?;
    let flat = scene.flatten_bvh();
    let basis = CamBasis::new(&scene.camera, aspect);
    eprintln!(
        "scene '{}': {} primitives, {} BVH nodes",
        args.scene, flat.prim_count, flat.node_count
    );

    // --- GPU setup ---------------------------------------------------------
    let ctx = CudaContext::new(0).context("no CUDA device (need an NVIDIA GPU + driver)")?;
    eprintln!("GPU: {}", ctx.name().unwrap_or_else(|_| "unknown".into()));
    let stream = ctx.default_stream();
    let ptx = compile_ptx(TRACE_SRC).context("NVRTC failed to compile the kernel")?;
    let module = ctx.load_module(ptx)?;
    let trace = module.load_function("trace")?;

    // --- Upload ------------------------------------------------------------
    let nbounds = stream.clone_htod(&flat.node_bounds)?;
    let nmeta = stream.clone_htod(&flat.node_meta)?;
    let pkind = stream.clone_htod(&flat.prim_kind)?;
    let pdata = stream.clone_htod(&flat.prim_data)?;
    let cam = stream.clone_htod(&basis.pack())?;
    let mut out_color = stream.alloc_zeros::<f32>(w * h * 3)?;
    let mut out_t = stream.alloc_zeros::<f32>(w * h)?;

    // --- Launch ------------------------------------------------------------
    const BLOCK: u32 = 16;
    let cfg = LaunchConfig {
        grid_dim: ((w as u32).div_ceil(BLOCK), (h as u32).div_ceil(BLOCK), 1),
        block_dim: (BLOCK, BLOCK, 1),
        shared_mem_bytes: 0,
    };
    let (wi, hi) = (w as i32, h as i32);
    let start = std::time::Instant::now();
    let mut launch = stream.launch_builder(&trace);
    launch
        .arg(&mut out_color)
        .arg(&mut out_t)
        .arg(&wi)
        .arg(&hi)
        .arg(&nbounds)
        .arg(&nmeta)
        .arg(&pkind)
        .arg(&pdata)
        .arg(&cam);
    unsafe { launch.launch(cfg)? };
    stream.synchronize()?;
    let gpu_ms = start.elapsed().as_secs_f64() * 1e3;

    // --- Read back + encode ------------------------------------------------
    let linear = stream.clone_dtoh(&out_color)?;
    let gpu_t = stream.clone_dtoh(&out_t)?;
    let mut bytes = vec![0u8; w * h * 3];
    for (px, chunk) in linear.chunks_exact(3).enumerate() {
        let rgb = to_srgb8(Vec3::new(chunk[0], chunk[1], chunk[2]), Tonemap::Clamp, 2.2);
        bytes[px * 3..px * 3 + 3].copy_from_slice(&rgb);
    }
    if let Some(dir) = args.output.parent() {
        std::fs::create_dir_all(dir).ok();
    }
    image::save_buffer(
        &args.output,
        &bytes,
        w as u32,
        h as u32,
        image::ExtendedColorType::Rgb8,
    )
    .with_context(|| format!("writing {}", args.output.display()))?;
    eprintln!(
        "GPU traced {w}x{h} in {gpu_ms:.3} ms -> {}",
        args.output.display()
    );

    // --- Validate against the CPU BVH --------------------------------------
    validate(&scene, &basis, &gpu_t, w, h);
    Ok(())
}

/// Compare the GPU's per-pixel hit distance against the CPU `Scene::hit` over
/// identical rays. Agreement confirms the GPU traversal is correct.
fn validate(scene: &Scene, basis: &CamBasis, gpu_t: &[f32], w: usize, h: usize) {
    let start = std::time::Instant::now();
    let (mut agree, mut max_diff, mut mismatched) = (0usize, 0.0f32, 0usize);
    for y in 0..h {
        for x in 0..w {
            let cpu = scene
                .hit(&basis.ray(x, y, w, h), 1e-3, Float::INFINITY)
                .map(|hr| hr.t)
                .unwrap_or(-1.0);
            let gpu = gpu_t[y * w + x];
            let both_miss = cpu < 0.0 && gpu < 0.0;
            let both_hit = cpu > 0.0 && gpu > 0.0;
            if both_miss {
                agree += 1;
            } else if both_hit {
                let diff = (cpu - gpu).abs();
                let rel = diff / cpu.abs().max(1e-4);
                if rel < 1e-3 {
                    agree += 1;
                } else {
                    mismatched += 1;
                }
                max_diff = max_diff.max(diff);
            } else {
                mismatched += 1; // hit/miss disagreement (silhouette pixel)
            }
        }
    }
    let total = w * h;
    let cpu_ms = start.elapsed().as_secs_f64() * 1e3;
    eprintln!(
        "validation vs CPU BVH: {:.4}% of {total} pixels agree ({mismatched} differ), \
         max hit-distance diff {max_diff:.3e}  (CPU reference took {cpu_ms:.0} ms)",
        100.0 * agree as f64 / total as f64
    );
}
