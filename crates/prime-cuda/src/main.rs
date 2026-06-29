//! `prime-gpu` — headless CUDA renderer CLI. Renders a scene to a PNG on the
//! GPU and (with `--validate`) compares it to the CPU renderer. All the rendering
//! lives in the `prime_cuda` library; this is a thin front-end.

use std::path::PathBuf;
use std::time::Instant;

use anyhow::{Context, Result};
use clap::Parser;
use prime_core::color::Tonemap;
use prime_core::integrator::{self, RenderSettings};
use prime_core::scene::Scene;
use prime_core::Float;
use prime_cuda::{load_scene, CamBasis, GpuScene};

#[derive(Parser, Debug)]
#[command(
    name = "prime-gpu",
    version,
    about = "Experimental CUDA path tracer for Prime"
)]
struct Args {
    /// Scene: `cornell`, `checker`, `image`, `env`, `cornell-diffuse`, a built-in
    /// demo (showcase/spheres/rtweekend/studio/sky), a `.ron`/`.gltf`/`.obj` file.
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
    /// Samples per pixel.
    #[arg(short, long, default_value_t = 256)]
    samples: usize,
    /// Maximum path depth.
    #[arg(short, long, default_value_t = 16)]
    depth: usize,
    /// RNG seed.
    #[arg(long, default_value_t = 0)]
    seed: u32,
    /// Also render on the CPU and report the RMSE between them.
    #[arg(long)]
    validate: bool,
    /// Lens aperture for depth of field (0 = pinhole). Overrides the scene camera.
    #[arg(long)]
    aperture: Option<Float>,
    /// Focus distance (defaults to the look-at distance).
    #[arg(long)]
    focus_dist: Option<Float>,
}

fn main() -> Result<()> {
    let args = Args::parse();
    let (w, h) = (args.width, args.height);
    let aspect = w as Float / h as Float;

    let mut scene =
        load_scene(&args.scene).with_context(|| format!("loading scene '{}'", args.scene))?;
    if let Some(a) = args.aperture {
        scene.camera.aperture = a;
    }
    if let Some(f) = args.focus_dist {
        scene.camera.focus_dist = Some(f);
    }

    let gpu = GpuScene::new(&scene, w, h, args.depth, args.seed)?;
    eprintln!("GPU: {}", gpu.device_name());
    let mut accum = gpu.alloc_accum()?;
    let cam = CamBasis::new(&scene.camera, aspect);

    let start = Instant::now();
    gpu.render_pass(&mut accum, &cam, args.samples as i32, 0)?;
    let bytes = gpu.resolve_srgb(&accum, args.samples as Float, Tonemap::Clamp, 2.2)?;
    eprintln!(
        "GPU path-traced {w}x{h} @ {} spp in {:.1} ms",
        args.samples,
        start.elapsed().as_secs_f64() * 1e3
    );

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
    eprintln!("wrote {}", args.output.display());

    if args.validate {
        validate(&scene, &args, &bytes);
    }
    Ok(())
}

/// Render the same scene on the CPU at the same settings and report RMSE.
fn validate(scene: &Scene, args: &Args, gpu: &[u8]) {
    let settings = RenderSettings {
        width: args.width,
        height: args.height,
        samples_per_pixel: args.samples,
        max_depth: args.depth,
        tonemap: Tonemap::Clamp,
        gamma: 2.2,
        ..Default::default()
    };
    let start = Instant::now();
    let cpu = integrator::render_to_srgb(scene, &settings, || {});
    let cpu_ms = start.elapsed().as_secs_f64() * 1e3;

    let n = gpu.len().min(cpu.len());
    let sse: f64 = (0..n)
        .map(|i| {
            let d = gpu[i] as f64 - cpu[i] as f64;
            d * d
        })
        .sum();
    let rmse = (sse / n as f64).sqrt();
    eprintln!(
        "validation vs CPU ({} spp): RMSE {rmse:.2} / 255 ({:.2}%)  [CPU render {cpu_ms:.0} ms]",
        args.samples,
        100.0 * rmse / 255.0
    );
}
