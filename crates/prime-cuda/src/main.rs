//! `prime-gpu` — experimental CUDA front-end for Prime (Phase A).
//!
//! This is the first step of the GPU renderer: it proves the full pipeline —
//! upload scene data, compile a kernel at runtime (NVRTC), launch it on the GPU,
//! read the framebuffer back, and write a PNG. The kernel currently does a
//! single primary ray per pixel against a handful of spheres (no path tracing).
//! Later phases port the BVH traversal and the full path tracer.
//!
//! Requires an NVIDIA GPU and the CUDA toolkit. Build/run with the CUDA path set:
//!   CUDA_PATH=/usr/local/cuda cargo run --manifest-path crates/prime-cuda/Cargo.toml --release

use std::path::PathBuf;

use anyhow::{Context, Result};
use clap::Parser;
use cudarc::driver::{CudaContext, LaunchConfig, PushKernelArg};
use cudarc::nvrtc::compile_ptx;
use prime_core::color::{to_srgb8, Tonemap};
use prime_core::math::Vec3;
use prime_core::Float;

const KERNEL_SRC: &str = include_str!("kernels/render.cu");

#[derive(Parser, Debug)]
#[command(name = "prime-gpu", version, about = "Experimental CUDA renderer for Prime")]
struct Args {
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

/// A demo sphere: position + radius + linear-RGB albedo.
struct Sphere {
    center: Vec3,
    radius: Float,
    albedo: Vec3,
}

/// The Phase-A demo scene: a ground plane (huge sphere) and three colored balls.
fn demo_spheres() -> Vec<Sphere> {
    vec![
        Sphere {
            center: Vec3::new(0.0, -1000.0, 0.0),
            radius: 1000.0,
            albedo: Vec3::new(0.5, 0.5, 0.5),
        },
        Sphere {
            center: Vec3::new(0.0, 1.0, 0.0),
            radius: 1.0,
            albedo: Vec3::new(0.8, 0.2, 0.2),
        },
        Sphere {
            center: Vec3::new(-2.2, 1.0, 0.0),
            radius: 1.0,
            albedo: Vec3::new(0.2, 0.3, 0.8),
        },
        Sphere {
            center: Vec3::new(2.2, 1.0, 0.0),
            radius: 1.0,
            albedo: Vec3::new(0.2, 0.7, 0.3),
        },
    ]
}

/// Pack the spheres into the flat f32 layout the kernel expects (8 floats each).
fn pack_spheres(spheres: &[Sphere]) -> Vec<f32> {
    let mut v = Vec::with_capacity(spheres.len() * 8);
    for s in spheres {
        v.extend_from_slice(&[
            s.center.x, s.center.y, s.center.z, s.radius, s.albedo.x, s.albedo.y, s.albedo.z, 0.0,
        ]);
    }
    v
}

/// Compute a pinhole camera frame and pack it as `[origin, lower_left,
/// horizontal, vertical]` (12 floats) — the same parameterization the kernel
/// uses to reconstruct ray directions.
fn pack_camera(aspect: Float) -> Vec<f32> {
    let look_from = Vec3::new(0.0, 1.5, 7.0);
    let look_at = Vec3::new(0.0, 1.0, 0.0);
    let vup = Vec3::new(0.0, 1.0, 0.0);
    let vfov: Float = 35.0;

    let theta = vfov.to_radians();
    let viewport_h = 2.0 * (theta / 2.0).tan();
    let viewport_w = aspect * viewport_h;

    let w = (look_from - look_at).normalize();
    let u = vup.cross(w).normalize();
    let v = w.cross(u);

    let horizontal = u * viewport_w;
    let vertical = v * viewport_h;
    let lower_left = look_from - horizontal * 0.5 - vertical * 0.5 - w;

    vec![
        look_from.x,
        look_from.y,
        look_from.z,
        lower_left.x,
        lower_left.y,
        lower_left.z,
        horizontal.x,
        horizontal.y,
        horizontal.z,
        vertical.x,
        vertical.y,
        vertical.z,
    ]
}

fn main() -> Result<()> {
    let args = Args::parse();
    let (w, h) = (args.width, args.height);

    // --- GPU setup ---------------------------------------------------------
    let ctx = CudaContext::new(0).context("no CUDA device (need an NVIDIA GPU + driver)")?;
    eprintln!("GPU: {}", ctx.name().unwrap_or_else(|_| "unknown".into()));
    let stream = ctx.default_stream();
    let ptx = compile_ptx(KERNEL_SRC).context("NVRTC failed to compile the kernel")?;
    let module = ctx.load_module(ptx)?;
    let render = module.load_function("render")?;

    // --- Upload scene ------------------------------------------------------
    let spheres = demo_spheres();
    let sph_host = pack_spheres(&spheres);
    let cam_host = pack_camera(w as Float / h as Float);
    let sph_dev = stream.clone_htod(&sph_host)?;
    let cam_dev = stream.clone_htod(&cam_host)?;
    let mut out_dev = stream.alloc_zeros::<f32>(w * h * 3)?;

    // --- Launch ------------------------------------------------------------
    const BLOCK: u32 = 16;
    let cfg = LaunchConfig {
        grid_dim: (
            (w as u32).div_ceil(BLOCK),
            (h as u32).div_ceil(BLOCK),
            1,
        ),
        block_dim: (BLOCK, BLOCK, 1),
        shared_mem_bytes: 0,
    };
    let (wi, hi, ni) = (w as i32, h as i32, spheres.len() as i32);
    let start = std::time::Instant::now();
    let mut launch = stream.launch_builder(&render);
    launch
        .arg(&mut out_dev)
        .arg(&wi)
        .arg(&hi)
        .arg(&sph_dev)
        .arg(&ni)
        .arg(&cam_dev);
    unsafe { launch.launch(cfg)? };
    stream.synchronize()?;
    let elapsed = start.elapsed();

    // --- Read back + encode ------------------------------------------------
    let linear = stream.clone_dtoh(&out_dev)?;
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
        "Rendered {}x{} on GPU in {:.3} ms -> {}",
        w,
        h,
        elapsed.as_secs_f64() * 1e3,
        args.output.display()
    );
    Ok(())
}
