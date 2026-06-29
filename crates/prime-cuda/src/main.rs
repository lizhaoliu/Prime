//! `prime-gpu` — experimental CUDA front-end for Prime.
//!
//! **Phase C (increment 4):** a GPU **path tracer** at near-parity with the CPU —
//! the full material set (Lambertian, GGX Metal, Dielectric, emissive), NEE +
//! MIS, and **albedo textures** (constant + procedural checkerboard, UV-mapped).
//! The kernel reuses the validated Phase-B BVH traversal and mirrors the CPU's
//! BSDFs + integrator, so the GPU image converges to the CPU reference;
//! `--validate` renders the same scene on the CPU and reports RMSE.
//!
//! Requires an NVIDIA GPU + CUDA toolkit (this crate is excluded from the
//! workspace). Build/run with the CUDA path set:
//!   CUDA_PATH=/usr/local/cuda cargo run --manifest-path crates/prime-cuda/Cargo.toml --release -- cornell -s 512

use std::path::{Path, PathBuf};

use anyhow::{bail, Context, Result};
use clap::Parser;
use cudarc::driver::{CudaContext, LaunchConfig, PushKernelArg};
use cudarc::nvrtc::compile_ptx;

use prime_core::aabb::Aabb;
use prime_core::camera::CameraConfig;
use prime_core::color::{to_srgb8, Tonemap};
use prime_core::geometry::{Primitive, Sphere, Triangle};
use prime_core::integrator::{self, RenderSettings};
use prime_core::material::Material;
use prime_core::math::Vec3;
use prime_core::scene::{Background, Scene};
use prime_core::texture::Texture;
use prime_core::{demo, obj, Color, Float, MaterialId};

const PATHTRACE_SRC: &str = include_str!("kernels/pathtrace.cu");

#[derive(Parser, Debug)]
#[command(
    name = "prime-gpu",
    version,
    about = "Experimental CUDA path tracer for Prime"
)]
struct Args {
    /// Scene: `cornell`, `checker`, `cornell-diffuse`, a built-in demo name
    /// (showcase/spheres/rtweekend/studio/sky), or a `.obj` mesh.
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
    /// Also render the scene on the CPU and report the RMSE between them.
    #[arg(long)]
    validate: bool,
}

fn main() -> Result<()> {
    let args = Args::parse();
    let (w, h) = (args.width, args.height);
    let aspect = w as Float / h as Float;

    let scene =
        load_scene(&args.scene).with_context(|| format!("loading scene '{}'", args.scene))?;
    let bytes = gpu_render(&scene, &args, aspect)?;

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

/// Render `scene` on the GPU and return tonemapped sRGB bytes.
fn gpu_render(scene: &Scene, args: &Args, aspect: Float) -> Result<Vec<u8>> {
    let (w, h) = (args.width, args.height);
    let flat = scene.flatten_bvh();
    let mats = flatten_materials(scene);
    let albedo_tex = flatten_albedo_tex(scene);
    let lights = emissive_prims(&flat, &mats);
    let n_lights = lights.len() as i32;
    // clone_htod needs a non-empty slice; the kernel ignores it when n_lights == 0.
    let lights_buf = if lights.is_empty() {
        vec![0u32]
    } else {
        lights
    };
    let (bg_kind, bg_vals) = background_data(&scene.background);
    let basis = CamBasis::new(&scene.camera, aspect);
    eprintln!(
        "scene '{}': {} primitives, {} BVH nodes, {} materials",
        args.scene,
        flat.prim_count,
        flat.node_count,
        scene.material_count()
    );

    let ctx = CudaContext::new(0).context("no CUDA device (need an NVIDIA GPU + driver)")?;
    eprintln!("GPU: {}", ctx.name().unwrap_or_else(|_| "unknown".into()));
    let stream = ctx.default_stream();
    let ptx = compile_ptx(PATHTRACE_SRC).context("NVRTC failed to compile the kernel")?;
    let module = ctx.load_module(ptx)?;
    let pathtrace = module.load_function("pathtrace")?;

    let nbounds = stream.clone_htod(&flat.node_bounds)?;
    let nmeta = stream.clone_htod(&flat.node_meta)?;
    let pkind = stream.clone_htod(&flat.prim_kind)?;
    let pdata = stream.clone_htod(&flat.prim_data)?;
    let prim_uv = stream.clone_htod(&flat.prim_uv)?;
    let pmat = stream.clone_htod(&flat.prim_material)?;
    let mats_dev = stream.clone_htod(&mats)?;
    let albedo_dev = stream.clone_htod(&albedo_tex)?;
    let lights_dev = stream.clone_htod(&lights_buf)?;
    let cam = stream.clone_htod(&basis.pack())?;
    let bg = stream.clone_htod(&bg_vals)?;
    let mut out_dev = stream.alloc_zeros::<f32>(w * h * 3)?;

    const BLOCK: u32 = 16;
    let cfg = LaunchConfig {
        grid_dim: ((w as u32).div_ceil(BLOCK), (h as u32).div_ceil(BLOCK), 1),
        block_dim: (BLOCK, BLOCK, 1),
        shared_mem_bytes: 0,
    };
    let (wi, hi, spp, depth) = (w as i32, h as i32, args.samples as i32, args.depth as i32);
    let bgk = bg_kind;
    let seed = args.seed;
    let start = std::time::Instant::now();
    let mut launch = stream.launch_builder(&pathtrace);
    launch
        .arg(&mut out_dev)
        .arg(&wi)
        .arg(&hi)
        .arg(&spp)
        .arg(&depth)
        .arg(&seed)
        .arg(&nbounds)
        .arg(&nmeta)
        .arg(&pkind)
        .arg(&pdata)
        .arg(&prim_uv)
        .arg(&pmat)
        .arg(&mats_dev)
        .arg(&albedo_dev)
        .arg(&lights_dev)
        .arg(&n_lights)
        .arg(&cam)
        .arg(&bgk)
        .arg(&bg);
    unsafe { launch.launch(cfg)? };
    stream.synchronize()?;
    let gpu_ms = start.elapsed().as_secs_f64() * 1e3;
    eprintln!(
        "GPU path-traced {w}x{h} @ {} spp in {gpu_ms:.1} ms",
        args.samples
    );

    let linear = stream.clone_dtoh(&out_dev)?;
    let mut bytes = vec![0u8; w * h * 3];
    for (px, chunk) in linear.chunks_exact(3).enumerate() {
        let rgb = to_srgb8(Vec3::new(chunk[0], chunk[1], chunk[2]), Tonemap::Clamp, 2.2);
        bytes[px * 3..px * 3 + 3].copy_from_slice(&rgb);
    }
    Ok(bytes)
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
    let start = std::time::Instant::now();
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

fn load_scene(name: &str) -> Result<Scene> {
    Ok(match name {
        "cornell" => demo::cornell_box(),
        "cornell-diffuse" => diffuse_cornell(),
        "checker" => checker_cornell(),
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

/// Material table for the GPU: 8 f32/material — kind, albedo.xyz, emit.xyz, param.
/// Kinds: 0 = Lambertian, 1 = Metal, 2 = Emissive, 3 = Dielectric. (Increment 1
/// shades 0 and 2; constant albedo is sampled at a fixed uv.)
fn flatten_materials(scene: &Scene) -> Vec<f32> {
    let mut v = Vec::with_capacity(scene.material_count() * 8);
    for i in 0..scene.material_count() {
        let (kind, alb, emit, param) = match scene.material(i) {
            Material::Lambertian { albedo, .. } => (0.0, albedo.sample(0.5, 0.5), Color::ZERO, 0.0),
            Material::Metal {
                albedo, roughness, ..
            } => (1.0, albedo.sample(0.5, 0.5), Color::ZERO, *roughness),
            Material::Emissive { emit } => (2.0, Color::ZERO, *emit, 0.0),
            Material::Dielectric { ior } => (3.0, Color::ONE, Color::ZERO, *ior),
        };
        v.extend_from_slice(&[kind, alb.x, alb.y, alb.z, emit.x, emit.y, emit.z, param]);
    }
    v
}

/// Per-material albedo texture descriptor for the GPU (8 f32 each):
/// `kind, c.xyz, d.xyz, scale`. kind 0 = constant color `c`; kind 1 = checker of
/// `c` (even) / `d` (odd) at `scale`. Image textures fall back to a constant
/// sample for now (GPU image textures are a later increment).
fn flatten_albedo_tex(scene: &Scene) -> Vec<f32> {
    fn desc(t: &Texture) -> (f32, Color, Color, f32) {
        match t {
            Texture::Constant(c) => (0.0, *c, Color::ZERO, 0.0),
            Texture::Checker { even, odd, scale } => (1.0, *even, *odd, *scale),
            Texture::Image { .. } => (0.0, t.sample(0.5, 0.5), Color::ZERO, 0.0),
        }
    }
    let mut v = Vec::with_capacity(scene.material_count() * 8);
    for i in 0..scene.material_count() {
        let (kind, c, d, scale) = match scene.material(i) {
            Material::Lambertian { albedo, .. } | Material::Metal { albedo, .. } => desc(albedo),
            _ => (0.0, Color::ZERO, Color::ZERO, 0.0),
        };
        v.extend_from_slice(&[kind, c.x, c.y, c.z, d.x, d.y, d.z, scale]);
    }
    v
}

/// Indices (in flattened order) of emissive primitives — the GPU light list for
/// next-event estimation. Matches the CPU's "one light per emissive primitive".
fn emissive_prims(flat: &prime_core::bvh::FlatBvh, mats: &[f32]) -> Vec<u32> {
    (0..flat.prim_count)
        .filter(|&i| mats[flat.prim_material[i] as usize * 8] == 2.0)
        .map(|i| i as u32)
        .collect()
}

fn background_data(bg: &Background) -> (i32, Vec<f32>) {
    match bg {
        Background::Solid(c) => (0, vec![c.x, c.y, c.z, 0.0, 0.0, 0.0]),
        Background::Gradient { bottom, top } => {
            (1, vec![bottom.x, bottom.y, bottom.z, top.x, top.y, top.z])
        }
    }
}

/// A diffuse-only Cornell box (the glass/metal spheres of the built-in scene
/// become diffuse), so increment 1 can render it correctly and validate it.
fn diffuse_cornell() -> Scene {
    let materials = vec![
        Material::Lambertian {
            albedo: Color::new(0.65, 0.05, 0.05).into(),
            normal: None,
        }, // 0 red
        Material::Lambertian {
            albedo: Color::new(0.12, 0.45, 0.15).into(),
            normal: None,
        }, // 1 green
        Material::Lambertian {
            albedo: Color::new(0.73, 0.73, 0.73).into(),
            normal: None,
        }, // 2 white
        Material::Emissive {
            emit: Color::splat(15.0),
        }, // 3 light
        Material::Lambertian {
            albedo: Color::new(0.2, 0.3, 0.8).into(),
            normal: None,
        }, // 4 blue
    ];
    const RED: MaterialId = 0;
    const GREEN: MaterialId = 1;
    const WHITE: MaterialId = 2;
    const LIGHT: MaterialId = 3;
    const BLUE: MaterialId = 4;

    let mut prims = Vec::new();
    let mut quad = |a, b, c, d, m| {
        prims.push(Primitive::from(Triangle::new(a, b, c, m)));
        prims.push(Primitive::from(Triangle::new(a, c, d, m)));
    };
    let p = Vec3::new;
    quad(
        p(555.0, 0.0, 0.0),
        p(555.0, 555.0, 0.0),
        p(555.0, 555.0, 555.0),
        p(555.0, 0.0, 555.0),
        GREEN,
    );
    quad(
        p(0.0, 0.0, 0.0),
        p(0.0, 0.0, 555.0),
        p(0.0, 555.0, 555.0),
        p(0.0, 555.0, 0.0),
        RED,
    );
    quad(
        p(0.0, 0.0, 0.0),
        p(555.0, 0.0, 0.0),
        p(555.0, 0.0, 555.0),
        p(0.0, 0.0, 555.0),
        WHITE,
    );
    quad(
        p(0.0, 555.0, 0.0),
        p(0.0, 555.0, 555.0),
        p(555.0, 555.0, 555.0),
        p(555.0, 555.0, 0.0),
        WHITE,
    );
    quad(
        p(0.0, 0.0, 555.0),
        p(555.0, 0.0, 555.0),
        p(555.0, 555.0, 555.0),
        p(0.0, 555.0, 555.0),
        WHITE,
    );
    quad(
        p(213.0, 554.0, 227.0),
        p(343.0, 554.0, 227.0),
        p(343.0, 554.0, 332.0),
        p(213.0, 554.0, 332.0),
        LIGHT,
    );
    prims.push(Primitive::from(Sphere::new(
        p(190.0, 90.0, 190.0),
        90.0,
        WHITE,
    )));
    prims.push(Primitive::from(Sphere::new(
        p(370.0, 90.0, 350.0),
        90.0,
        BLUE,
    )));

    let camera = CameraConfig {
        look_from: Vec3::new(278.0, 278.0, -800.0),
        look_at: Vec3::new(278.0, 278.0, 0.0),
        vup: Vec3::new(0.0, 1.0, 0.0),
        vfov: 40.0,
        aperture: 0.0,
        focus_dist: None,
    };
    Scene::new(materials, prims, camera, Background::Solid(Color::ZERO))
}

/// A Cornell box with a UV-mapped checkerboard floor, for validating GPU
/// textures against the CPU.
fn checker_cornell() -> Scene {
    let materials = vec![
        Material::Lambertian {
            albedo: Color::new(0.65, 0.05, 0.05).into(),
            normal: None,
        }, // 0 red
        Material::Lambertian {
            albedo: Color::new(0.12, 0.45, 0.15).into(),
            normal: None,
        }, // 1 green
        Material::Lambertian {
            albedo: Color::new(0.73, 0.73, 0.73).into(),
            normal: None,
        }, // 2 white
        Material::Emissive {
            emit: Color::splat(15.0),
        }, // 3 light
        Material::Lambertian {
            albedo: Texture::Checker {
                even: Color::new(0.9, 0.9, 0.9),
                odd: Color::new(0.1, 0.1, 0.1),
                scale: 6.0,
            },
            normal: None,
        }, // 4 checker
    ];
    const RED: MaterialId = 0;
    const GREEN: MaterialId = 1;
    const WHITE: MaterialId = 2;
    const LIGHT: MaterialId = 3;
    const CHECKER: MaterialId = 4;
    let p = Vec3::new;

    let mut prims = Vec::new();
    {
        let mut quad = |a, b, c, d, m| {
            prims.push(Primitive::from(Triangle::new(a, b, c, m)));
            prims.push(Primitive::from(Triangle::new(a, c, d, m)));
        };
        quad(
            p(555.0, 0.0, 0.0),
            p(555.0, 555.0, 0.0),
            p(555.0, 555.0, 555.0),
            p(555.0, 0.0, 555.0),
            GREEN,
        );
        quad(
            p(0.0, 0.0, 0.0),
            p(0.0, 0.0, 555.0),
            p(0.0, 555.0, 555.0),
            p(0.0, 555.0, 0.0),
            RED,
        );
        quad(
            p(0.0, 555.0, 0.0),
            p(0.0, 555.0, 555.0),
            p(555.0, 555.0, 555.0),
            p(555.0, 555.0, 0.0),
            WHITE,
        );
        quad(
            p(0.0, 0.0, 555.0),
            p(555.0, 0.0, 555.0),
            p(555.0, 555.0, 555.0),
            p(0.0, 555.0, 555.0),
            WHITE,
        );
        quad(
            p(213.0, 554.0, 227.0),
            p(343.0, 554.0, 227.0),
            p(343.0, 554.0, 332.0),
            p(213.0, 554.0, 332.0),
            LIGHT,
        );
    }
    // Checkerboard floor, UV-mapped across the whole quad.
    let (f0, f1, f2, f3) = (
        p(0.0, 0.0, 0.0),
        p(555.0, 0.0, 0.0),
        p(555.0, 0.0, 555.0),
        p(0.0, 0.0, 555.0),
    );
    prims.push(Primitive::from(
        Triangle::new(f0, f1, f2, CHECKER).with_uvs([[0.0, 0.0], [1.0, 0.0], [1.0, 1.0]]),
    ));
    prims.push(Primitive::from(
        Triangle::new(f0, f2, f3, CHECKER).with_uvs([[0.0, 0.0], [1.0, 1.0], [0.0, 1.0]]),
    ));
    prims.push(Primitive::from(Sphere::new(
        p(278.0, 120.0, 290.0),
        120.0,
        WHITE,
    )));

    let camera = CameraConfig {
        look_from: Vec3::new(278.0, 278.0, -800.0),
        look_at: Vec3::new(278.0, 278.0, 0.0),
        vup: Vec3::new(0.0, 1.0, 0.0),
        vfov: 40.0,
        aperture: 0.0,
        focus_dist: None,
    };
    Scene::new(materials, prims, camera, Background::Solid(Color::ZERO))
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
/// packed as `[origin, lower_left, horizontal, vertical]`.
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
}
