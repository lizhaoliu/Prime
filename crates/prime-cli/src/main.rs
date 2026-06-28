//! `prime` — command-line front-end for the headless path tracer.
//!
//! Renders a built-in demo, a RON scene description, or a bare OBJ mesh to a
//! PNG. All rendering lives in `prime-core`; this binary only parses arguments,
//! loads the scene, drives a progress bar, and writes the image.

use std::path::{Path, PathBuf};
use std::time::Instant;

use anyhow::{bail, Context, Result};
use clap::{Parser, ValueEnum};
use indicatif::{ProgressBar, ProgressStyle};

use prime_core::aabb::Aabb;
use prime_core::camera::CameraConfig;
use prime_core::color::Tonemap;
use prime_core::desc::SceneDesc;
use prime_core::geometry::Primitive;
use prime_core::material::Material;
use prime_core::math::Vec3;
use prime_core::obj;
use prime_core::scene::{Background, Scene};
use prime_core::{demo, integrator, Color, Float};

/// A headless physically based path tracer.
#[derive(Parser, Debug)]
#[command(name = "prime", version, about, long_about = None)]
struct Args {
    /// Scene to render: a built-in name (`showcase`, `studio`, `rtweekend`,
    /// `cornell`, `spheres`), a `.ron` scene file, or a `.obj` mesh.
    #[arg(default_value = "showcase")]
    scene: String,

    /// Output PNG path.
    #[arg(short, long, default_value = "out.png")]
    output: PathBuf,

    /// Image width in pixels.
    #[arg(short, long, default_value_t = 800)]
    width: usize,

    /// Image height in pixels.
    #[arg(long, default_value_t = 450)]
    height: usize,

    /// Samples per pixel (higher = less noise, slower).
    #[arg(short, long, default_value_t = 64)]
    samples: usize,

    /// Maximum path bounce depth.
    #[arg(short, long, default_value_t = 32)]
    depth: usize,

    /// RNG seed; the same seed reproduces the same image.
    #[arg(long, default_value_t = 0)]
    seed: u64,

    /// Number of worker threads (default: all logical cores).
    #[arg(short = 'j', long)]
    threads: Option<usize>,

    /// Tonemapping operator.
    #[arg(long, value_enum, default_value_t = TonemapArg::Clamp)]
    tonemap: TonemapArg,

    /// Display gamma.
    #[arg(long, default_value_t = 2.2)]
    gamma: Float,

    /// Clamp each path sample's radiance to suppress fireflies (0 = disabled,
    /// keeping the render unbiased).
    #[arg(long, default_value_t = 0.0)]
    clamp: Float,

    /// Use plain white-noise sampling instead of the low-discrepancy (QMC)
    /// sampler. Mostly useful for comparison.
    #[arg(long)]
    no_qmc: bool,
}

#[derive(Copy, Clone, Debug, ValueEnum)]
enum TonemapArg {
    Clamp,
    Reinhard,
}

impl From<TonemapArg> for Tonemap {
    fn from(t: TonemapArg) -> Self {
        match t {
            TonemapArg::Clamp => Tonemap::Clamp,
            TonemapArg::Reinhard => Tonemap::Reinhard,
        }
    }
}

fn main() -> Result<()> {
    let args = Args::parse();

    if let Some(n) = args.threads {
        rayon::ThreadPoolBuilder::new()
            .num_threads(n)
            .build_global()
            .context("failed to configure the thread pool")?;
    }

    let aspect = args.width as Float / args.height as Float;
    let scene = load_scene(&args.scene, aspect)
        .with_context(|| format!("loading scene '{}'", args.scene))?;

    let settings = integrator::RenderSettings {
        width: args.width,
        height: args.height,
        samples_per_pixel: args.samples,
        max_depth: args.depth,
        seed: args.seed,
        low_discrepancy: !args.no_qmc,
        firefly_clamp: args.clamp,
        tonemap: args.tonemap.into(),
        gamma: args.gamma,
    };

    eprintln!(
        "Rendering '{}': {}x{}, {} spp, depth {}, {} primitives, {} threads",
        args.scene,
        settings.width,
        settings.height,
        settings.samples_per_pixel,
        settings.max_depth,
        scene.primitive_count(),
        rayon::current_num_threads(),
    );

    let pb = ProgressBar::new(settings.height as u64);
    pb.set_style(
        ProgressStyle::with_template(
            "{spinner} [{elapsed_precise}] [{bar:40.cyan/blue}] {pos}/{len} rows ({eta})",
        )
        .unwrap()
        .progress_chars("=>-"),
    );

    let start = Instant::now();
    let bytes = integrator::render_to_srgb(&scene, &settings, || pb.inc(1));
    pb.finish_and_clear();
    let elapsed = start.elapsed();

    image::save_buffer(
        &args.output,
        &bytes,
        settings.width as u32,
        settings.height as u32,
        image::ExtendedColorType::Rgb8,
    )
    .with_context(|| format!("writing {}", args.output.display()))?;

    eprintln!(
        "Done in {:.2}s -> {}",
        elapsed.as_secs_f64(),
        args.output.display()
    );
    Ok(())
}

/// Resolve the `scene` argument into a [`Scene`]. `aspect` is used only when
/// auto-framing a bare OBJ mesh.
fn load_scene(source: &str, aspect: Float) -> Result<Scene> {
    match source {
        "showcase" => return Ok(demo::showcase()),
        "cornell" => return Ok(demo::cornell_box()),
        "spheres" => return Ok(demo::spheres()),
        "rtweekend" => return Ok(demo::rtweekend()),
        "studio" => return Ok(demo::studio()),
        _ => {}
    }

    let path = Path::new(source);
    match path.extension().and_then(|e| e.to_str()) {
        Some("ron") => load_ron_scene(path),
        Some("obj") => load_obj_scene(path, aspect),
        _ => bail!(
            "unknown scene '{source}': expected a built-in name (showcase, studio, \
             rtweekend, cornell, spheres), a .ron scene, or a .obj mesh"
        ),
    }
}

fn load_ron_scene(path: &Path) -> Result<Scene> {
    let text = std::fs::read_to_string(path)
        .with_context(|| format!("reading scene file {}", path.display()))?;
    let desc: SceneDesc = ron::from_str(&text).context("parsing RON scene")?;
    let base_dir = path.parent().unwrap_or_else(|| Path::new("."));
    let scene = desc.build(base_dir).context("building scene")?;
    Ok(scene)
}

/// Wrap a bare OBJ mesh in a scene: a neutral diffuse material, a sky-gradient
/// background, and a camera auto-framed to the mesh bounds.
fn load_obj_scene(path: &Path, aspect: Float) -> Result<Scene> {
    let material = Material::Lambertian {
        albedo: Color::new(0.73, 0.73, 0.73),
    };
    let triangles = obj::load(path, 0, obj::Transform::default())
        .with_context(|| format!("loading OBJ mesh {}", path.display()))?;
    if triangles.is_empty() {
        bail!("OBJ mesh {} contains no triangles", path.display());
    }

    let bounds = triangles.iter().fold(Aabb::EMPTY, |b, t| b.union(t.aabb()));
    let camera = frame_camera(bounds, aspect);

    let prims: Vec<Primitive> = triangles.into_iter().map(Primitive::from).collect();
    Ok(Scene::new(
        vec![material],
        prims,
        camera,
        Background::default(),
    ))
}

/// Pick a camera that frames an axis-aligned box from a front, slightly raised
/// three-quarter view. Fits the bounding sphere to whichever of the horizontal
/// or vertical FOV is tighter, with a modest zoom-in so the subject is
/// prominent (scenes with a large ground plane otherwise read as mostly empty).
fn frame_camera(bounds: Aabb, aspect: Float) -> CameraConfig {
    let center = bounds.centroid();
    let radius = ((bounds.max - bounds.min).length() * 0.5).max(1e-3);
    let vfov: Float = 40.0;
    let vfov_rad = vfov.to_radians();
    let hfov_rad = 2.0 * (aspect * (vfov_rad * 0.5).tan()).atan();
    let half_fov = 0.5 * vfov_rad.min(hfov_rad);
    let fit_dist = radius / half_fov.sin();

    let dir = Vec3::new(0.35, 0.28, 1.0).normalize();
    let look_from = center + dir * fit_dist * 0.62;
    CameraConfig {
        look_from,
        look_at: center,
        vup: Vec3::new(0.0, 1.0, 0.0),
        vfov,
        aperture: 0.0,
        focus_dist: None,
    }
}
