//! `prime-cuda` — the GPU (CUDA) renderer for Prime, shared by the `prime-gpu`
//! CLI and the `prime-gpu-serve` interactive viewer.
//!
//! A GPU path tracer at feature parity with the CPU: full materials (Lambertian,
//! GGX Metal, Dielectric, emissive), NEE + MIS, textures (constant / checker /
//! image), environment lighting, and thin-lens depth of field. The kernel
//! accumulates into a buffer keyed by a sample offset, so passes refine the same
//! image progressively (used by the viewer). Requires an NVIDIA GPU + CUDA
//! toolkit, so the crate is excluded from the workspace.

use std::path::Path;
use std::sync::Arc;

use anyhow::{bail, Context, Result};
use cudarc::driver::{
    CudaContext, CudaFunction, CudaSlice, CudaStream, LaunchConfig, PushKernelArg,
};
use cudarc::nvrtc::compile_ptx;

use prime_core::aabb::Aabb;
use prime_core::camera::CameraConfig;
use prime_core::color::{to_srgb8, Tonemap};
use prime_core::desc::SceneDesc;
use prime_core::env::EnvMap;
use prime_core::geometry::{Primitive, Sphere, Triangle};
use prime_core::material::Material;
use prime_core::math::Vec3;
use prime_core::scene::{Background, Scene};
use prime_core::texture::{ImageData, Texture};
use prime_core::{demo, obj, Color, Float, MaterialId};

const PATHTRACE_SRC: &str = include_str!("kernels/pathtrace.cu");

/// A scene uploaded to the GPU: device buffers + the compiled kernel, ready to
/// render passes. Built once; `render_pass` accumulates samples into a caller-
/// owned buffer so the same image can be refined progressively (the viewer) or
/// in a single shot (the CLI).
pub struct GpuScene {
    stream: Arc<CudaStream>,
    func: CudaFunction,
    nbounds: CudaSlice<f32>,
    nmeta: CudaSlice<u32>,
    pkind: CudaSlice<u32>,
    pdata: CudaSlice<f32>,
    prim_uv: CudaSlice<f32>,
    pmat: CudaSlice<u32>,
    mats: CudaSlice<f32>,
    albedo: CudaSlice<f32>,
    tex_pixels: CudaSlice<f32>,
    lights: CudaSlice<u32>,
    bg: CudaSlice<f32>,
    env: CudaSlice<f32>,
    n_lights: i32,
    bg_kind: i32,
    env_w: i32,
    env_h: i32,
    env_intensity: f32,
    env_rotation: f32,
    width: usize,
    height: usize,
    depth: i32,
    seed: u32,
    _ctx: Arc<CudaContext>,
}

impl GpuScene {
    /// Upload `scene` to the GPU and compile the kernel.
    pub fn new(
        scene: &Scene,
        width: usize,
        height: usize,
        depth: usize,
        seed: u32,
    ) -> Result<Self> {
        let flat = scene.flatten_bvh();
        let mats = flatten_materials(scene);
        let (albedo_tex, tex_pixels) = flatten_albedo_tex(scene);
        let lights = emissive_prims(&flat, &mats);
        let n_lights = lights.len() as i32;
        let lights_buf = if lights.is_empty() {
            vec![0u32]
        } else {
            lights
        };
        let (bg_kind, bg_vals) = background_data(&scene.background);
        let (env_w, env_h, env_intensity, env_rotation, env_host) = match scene.environment() {
            Some(e) => {
                let mut px = Vec::with_capacity(e.width() * e.height() * 3);
                for c in e.pixels() {
                    px.extend_from_slice(&[c.x, c.y, c.z]);
                }
                (
                    e.width() as i32,
                    e.height() as i32,
                    e.intensity(),
                    e.rotation(),
                    px,
                )
            }
            None => (0, 0, 1.0, 0.0, vec![0.0, 0.0, 0.0]),
        };

        let ctx = CudaContext::new(0).context("no CUDA device (need an NVIDIA GPU + driver)")?;
        let stream = ctx.default_stream();
        let ptx = compile_ptx(PATHTRACE_SRC).context("NVRTC failed to compile the kernel")?;
        let module = ctx.load_module(ptx)?;
        let func = module.load_function("pathtrace")?;

        Ok(GpuScene {
            nbounds: stream.clone_htod(&flat.node_bounds)?,
            nmeta: stream.clone_htod(&flat.node_meta)?,
            pkind: stream.clone_htod(&flat.prim_kind)?,
            pdata: stream.clone_htod(&flat.prim_data)?,
            prim_uv: stream.clone_htod(&flat.prim_uv)?,
            pmat: stream.clone_htod(&flat.prim_material)?,
            mats: stream.clone_htod(&mats)?,
            albedo: stream.clone_htod(&albedo_tex)?,
            tex_pixels: stream.clone_htod(&tex_pixels)?,
            lights: stream.clone_htod(&lights_buf)?,
            bg: stream.clone_htod(&bg_vals)?,
            env: stream.clone_htod(&env_host)?,
            n_lights,
            bg_kind,
            env_w,
            env_h,
            env_intensity,
            env_rotation,
            width,
            height,
            depth: depth as i32,
            seed,
            func,
            stream,
            _ctx: ctx,
        })
    }

    pub fn device_name(&self) -> String {
        self._ctx.name().unwrap_or_else(|_| "unknown".into())
    }

    /// A zeroed accumulation buffer (`width * height * 3` floats).
    pub fn alloc_accum(&self) -> Result<CudaSlice<f32>> {
        Ok(self
            .stream
            .alloc_zeros::<f32>(self.width * self.height * 3)?)
    }

    /// Render `spp` more samples (offset by `sample_base` for decorrelation),
    /// adding them into `accum`.
    pub fn render_pass(
        &self,
        accum: &mut CudaSlice<f32>,
        cam: &CamBasis,
        spp: i32,
        sample_base: i32,
    ) -> Result<()> {
        let cam_dev = self.stream.clone_htod(&cam.pack())?;
        const BLOCK: u32 = 16;
        let cfg = LaunchConfig {
            grid_dim: (
                (self.width as u32).div_ceil(BLOCK),
                (self.height as u32).div_ceil(BLOCK),
                1,
            ),
            block_dim: (BLOCK, BLOCK, 1),
            shared_mem_bytes: 0,
        };
        let (wi, hi) = (self.width as i32, self.height as i32);
        let (bgk, depth, seed, n_lights) = (self.bg_kind, self.depth, self.seed, self.n_lights);
        let (env_w, env_h, env_i, env_r) = (
            self.env_w,
            self.env_h,
            self.env_intensity,
            self.env_rotation,
        );
        let mut launch = self.stream.launch_builder(&self.func);
        launch
            .arg(accum)
            .arg(&wi)
            .arg(&hi)
            .arg(&spp)
            .arg(&sample_base)
            .arg(&depth)
            .arg(&seed)
            .arg(&self.nbounds)
            .arg(&self.nmeta)
            .arg(&self.pkind)
            .arg(&self.pdata)
            .arg(&self.prim_uv)
            .arg(&self.pmat)
            .arg(&self.mats)
            .arg(&self.albedo)
            .arg(&self.tex_pixels)
            .arg(&self.lights)
            .arg(&n_lights)
            .arg(&cam_dev)
            .arg(&bgk)
            .arg(&self.bg)
            .arg(&env_w)
            .arg(&env_h)
            .arg(&env_i)
            .arg(&env_r)
            .arg(&self.env);
        unsafe { launch.launch(cfg)? };
        self.stream.synchronize()?;
        Ok(())
    }

    /// Read back `accum`, divide by `total_spp`, and tonemap to sRGB bytes.
    pub fn resolve_srgb(
        &self,
        accum: &CudaSlice<f32>,
        total_spp: Float,
        tonemap: Tonemap,
        gamma: Float,
    ) -> Result<Vec<u8>> {
        let linear = self.stream.clone_dtoh(accum)?;
        let inv = 1.0 / total_spp.max(1.0);
        let mut bytes = vec![0u8; self.width * self.height * 3];
        for (px, chunk) in linear.chunks_exact(3).enumerate() {
            let c = Vec3::new(chunk[0], chunk[1], chunk[2]) * inv;
            bytes[px * 3..px * 3 + 3].copy_from_slice(&to_srgb8(c, tonemap, gamma));
        }
        Ok(bytes)
    }
}

/// Resolve a scene name (built-in / `.ron` / `.gltf` / `.obj`) to a [`Scene`].
pub fn load_scene(name: &str) -> Result<Scene> {
    Ok(match name {
        "cornell" => demo::cornell_box(),
        "cornell-diffuse" => diffuse_cornell(),
        "checker" => checker_cornell(),
        "image" => image_cornell()?,
        "env" => env_scene(),
        "showcase" => demo::showcase(),
        "spheres" => demo::spheres(),
        "rtweekend" => demo::rtweekend(),
        "studio" => demo::studio(),
        "sky" => demo::sky(),
        other if other.ends_with(".ron") => load_ron(Path::new(other))?,
        other if other.ends_with(".gltf") || other.ends_with(".glb") => {
            prime_gltf::load(other).map_err(|e| anyhow::anyhow!("loading glTF: {e}"))?
        }
        other if other.ends_with(".obj") => load_obj(Path::new(other))?,
        other => {
            bail!("unknown scene '{other}' (try cornell/checker/image/env or a .ron/.gltf/.obj)")
        }
    })
}

/// Decode an image file into linear-ish RGB pixels (textures + HDR), matching the
/// CLI. sRGB→linear for color textures happens later in `Texture::resolve`.
fn decode_image(path: &Path) -> Result<ImageData, String> {
    let img = image::open(path).map_err(|e| e.to_string())?.into_rgb32f();
    let (w, h) = img.dimensions();
    let pixels = img
        .pixels()
        .map(|p| Color::new(p.0[0], p.0[1], p.0[2]))
        .collect();
    Ok(ImageData {
        width: w as usize,
        height: h as usize,
        pixels,
    })
}

/// Load a RON scene, resolving image textures relative to the scene file.
fn load_ron(path: &Path) -> Result<Scene> {
    let text = std::fs::read_to_string(path)
        .with_context(|| format!("reading scene file {}", path.display()))?;
    let desc: SceneDesc = ron::from_str(&text).context("parsing RON scene")?;
    let base_dir = path.parent().unwrap_or_else(|| Path::new("."));
    desc.build(base_dir, &mut decode_image)
        .map_err(|e| anyhow::anyhow!("building scene: {e}"))
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

/// Per-material albedo texture descriptors (8 f32 each) plus a shared pixel pool
/// for image textures. Descriptor: `kind` then params. kind 0 = constant color
/// `c.xyz`; kind 1 = checker `even.xyz / odd.xyz` at `scale`; kind 2 = image
/// (`width, height, texel-offset` into the returned pool, linear RGB).
fn flatten_albedo_tex(scene: &Scene) -> (Vec<f32>, Vec<f32>) {
    let mut desc = Vec::with_capacity(scene.material_count() * 8);
    let mut pool: Vec<f32> = Vec::new();
    for i in 0..scene.material_count() {
        let albedo = match scene.material(i) {
            Material::Lambertian { albedo, .. } | Material::Metal { albedo, .. } => Some(albedo),
            _ => None,
        };
        match albedo {
            Some(Texture::Constant(c)) => {
                desc.extend_from_slice(&[0.0, c.x, c.y, c.z, 0.0, 0.0, 0.0, 0.0])
            }
            Some(Texture::Checker { even, odd, scale }) => {
                desc.extend_from_slice(&[1.0, even.x, even.y, even.z, odd.x, odd.y, odd.z, *scale])
            }
            Some(Texture::Image {
                data: Some(img), ..
            }) => {
                let off = (pool.len() / 3) as f32;
                for px in img.pixels() {
                    pool.extend_from_slice(&[px.x, px.y, px.z]);
                }
                desc.extend_from_slice(&[
                    2.0,
                    img.width() as f32,
                    img.height() as f32,
                    off,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                ]);
            }
            other => {
                // Unresolved image or non-textured material: constant fallback.
                let c = other.map_or(Color::ZERO, |t| t.sample(0.5, 0.5));
                desc.extend_from_slice(&[0.0, c.x, c.y, c.z, 0.0, 0.0, 0.0, 0.0]);
            }
        }
    }
    if pool.is_empty() {
        pool.extend_from_slice(&[0.0, 0.0, 0.0]); // clone_htod needs non-empty
    }
    (desc, pool)
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

/// A Cornell box whose floor is an image texture (a committed render), for
/// validating GPU image textures against the CPU. The image is resolved (decoded)
/// here, just as a RON/glTF loader would.
fn image_cornell() -> Result<Scene> {
    let mut floor = Material::Lambertian {
        albedo: Texture::Image {
            path: "docs/renders/sky.png".into(),
            srgb: true,
            data: None,
        },
        normal: None,
    };
    floor
        .resolve_textures(Path::new("."), &mut decode_image)
        .map_err(|e| anyhow::anyhow!("resolving floor texture: {e}"))?;

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
        floor, // 4 image
    ];
    const RED: MaterialId = 0;
    const GREEN: MaterialId = 1;
    const WHITE: MaterialId = 2;
    const LIGHT: MaterialId = 3;
    const IMAGE: MaterialId = 4;
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
    let (f0, f1, f2, f3) = (
        p(0.0, 0.0, 0.0),
        p(555.0, 0.0, 0.0),
        p(555.0, 0.0, 555.0),
        p(0.0, 0.0, 555.0),
    );
    prims.push(Primitive::from(
        Triangle::new(f0, f1, f2, IMAGE).with_uvs([[0.0, 0.0], [1.0, 0.0], [1.0, 1.0]]),
    ));
    prims.push(Primitive::from(
        Triangle::new(f0, f2, f3, IMAGE).with_uvs([[0.0, 0.0], [1.0, 1.0], [0.0, 1.0]]),
    ));

    let camera = CameraConfig {
        look_from: Vec3::new(278.0, 278.0, -800.0),
        look_at: Vec3::new(278.0, 278.0, 0.0),
        vup: Vec3::new(0.0, 1.0, 0.0),
        vfov: 40.0,
        aperture: 0.0,
        focus_dist: None,
    };
    Ok(Scene::new(
        materials,
        prims,
        camera,
        Background::Solid(Color::ZERO),
    ))
}

/// Diffuse spheres lit purely by a smooth gradient environment map (no sun), for
/// validating GPU environment lighting against the CPU.
fn env_scene() -> Scene {
    let materials = vec![
        Material::Lambertian {
            albedo: Color::new(0.6, 0.6, 0.6).into(),
            normal: None,
        }, // ground
        Material::Lambertian {
            albedo: Color::new(0.8, 0.3, 0.3).into(),
            normal: None,
        },
        Material::Lambertian {
            albedo: Color::new(0.3, 0.5, 0.8).into(),
            normal: None,
        },
    ];
    let prims = vec![
        Primitive::from(Sphere::new(Vec3::new(0.0, -1000.0, 0.0), 1000.0, 0)),
        Primitive::from(Sphere::new(Vec3::new(-1.2, 0.5, 0.0), 0.5, 1)),
        Primitive::from(Sphere::new(Vec3::new(1.2, 0.5, 0.0), 0.5, 2)),
    ];
    let camera = CameraConfig {
        look_from: Vec3::new(0.0, 1.0, 5.0),
        look_at: Vec3::new(0.0, 0.5, 0.0),
        vup: Vec3::new(0.0, 1.0, 0.0),
        vfov: 35.0,
        aperture: 0.0,
        focus_dist: None,
    };
    let mut scene = Scene::new(materials, prims, camera, Background::Solid(Color::ZERO));

    // A smooth zenith-to-horizon gradient (no concentrated sun), so plain BSDF
    // sampling converges quickly (GPU env importance sampling is a follow-up).
    let (w, h) = (64usize, 32usize);
    let mut px = vec![Color::ZERO; w * h];
    for v in 0..h {
        let t = v as Float / (h - 1) as Float; // 0 = zenith (top), 1 = nadir
        let col = Color::new(0.3, 0.5, 0.9) * (1.0 - t) + Color::new(1.0, 1.0, 1.0) * t;
        for u in 0..w {
            px[v * w + u] = col;
        }
    }
    scene.set_environment(EnvMap::from_pixels(w, h, px, 1.0, 0.0));
    scene
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

/// A thin-lens camera frame matching `prime_core::Camera`, packed as
/// `[origin, lower_left, horizontal, vertical, lens_u, lens_v, lens_radius]`
/// (19 floats). `aperture = 0` collapses to a pinhole.
pub struct CamBasis {
    origin: Vec3,
    lower_left: Vec3,
    horizontal: Vec3,
    vertical: Vec3,
    u: Vec3,
    v: Vec3,
    lens_radius: Float,
}

impl CamBasis {
    pub fn new(cfg: &CameraConfig, aspect: Float) -> CamBasis {
        let theta = cfg.vfov.to_radians();
        let h = (theta / 2.0).tan();
        let viewport_h = 2.0 * h;
        let viewport_w = aspect * viewport_h;
        let w = (cfg.look_from - cfg.look_at).normalize();
        let u = cfg.vup.cross(w).normalize();
        let v = w.cross(u);
        let focus_dist = cfg
            .focus_dist
            .unwrap_or_else(|| (cfg.look_from - cfg.look_at).length());
        let horizontal = u * (viewport_w * focus_dist);
        let vertical = v * (viewport_h * focus_dist);
        let lower_left = cfg.look_from - horizontal * 0.5 - vertical * 0.5 - w * focus_dist;
        CamBasis {
            origin: cfg.look_from,
            lower_left,
            horizontal,
            vertical,
            u,
            v,
            lens_radius: cfg.aperture / 2.0,
        }
    }

    pub fn pack(&self) -> Vec<f32> {
        let g = |p: Vec3| [p.x, p.y, p.z];
        [
            g(self.origin).as_slice(),
            &g(self.lower_left),
            &g(self.horizontal),
            &g(self.vertical),
            &g(self.u),
            &g(self.v),
            &[self.lens_radius],
        ]
        .concat()
    }
}
