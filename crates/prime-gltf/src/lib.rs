//! glTF / GLB scene loader for Prime.
//!
//! Imports a `.gltf`/`.glb` file into a [`prime_core::scene::Scene`]: it walks the
//! node graph (composing transforms), reads triangle meshes (positions, normals,
//! UVs), maps glTF metallic-roughness materials onto Prime's simpler BSDF set,
//! and decodes base-color textures. The result renders on both the CPU and the
//! GPU back-ends (which consume the same `Scene`).
//!
//! Prime's material model is deliberately small (Lambertian / GGX metal /
//! dielectric / emissive), so the mapping is an approximation: metallic-roughness
//! and emissive *textures*, normal maps, and transmission are not represented;
//! base color (factor + texture) and the metallic/roughness/emissive *factors*
//! are.

use std::sync::Arc;

use prime_core::camera::CameraConfig;
use prime_core::geometry::{Primitive, Triangle};
use prime_core::material::Material;
use prime_core::math::Vec3;
use prime_core::scene::{Background, Scene};
use prime_core::texture::{ImageTexture, Texture};
use prime_core::{Color, Float, MaterialId};

#[derive(Debug, thiserror::Error)]
pub enum GltfError {
    #[error("glTF import failed: {0}")]
    Import(#[from] gltf::Error),
    #[error("glTF contains no triangle geometry")]
    Empty,
}

/// 4×4 column-major matrix (glTF convention): `m[col][row]`.
type Mat4 = [[f32; 4]; 4];

const IDENTITY: Mat4 = [
    [1.0, 0.0, 0.0, 0.0],
    [0.0, 1.0, 0.0, 0.0],
    [0.0, 0.0, 1.0, 0.0],
    [0.0, 0.0, 0.0, 1.0],
];

fn mat_mul(a: &Mat4, b: &Mat4) -> Mat4 {
    let mut r = [[0.0f32; 4]; 4];
    for c in 0..4 {
        for row in 0..4 {
            r[c][row] = (0..4).map(|k| a[k][row] * b[c][k]).sum();
        }
    }
    r
}

fn transform_point(m: &Mat4, p: [f32; 3]) -> Vec3 {
    Vec3::new(
        m[0][0] * p[0] + m[1][0] * p[1] + m[2][0] * p[2] + m[3][0],
        m[0][1] * p[0] + m[1][1] * p[1] + m[2][1] * p[2] + m[3][1],
        m[0][2] * p[0] + m[1][2] * p[1] + m[2][2] * p[2] + m[3][2],
    )
}

fn transform_dir(m: &Mat4, v: [f32; 3]) -> Vec3 {
    // Rigid / uniform-scale approximation (no inverse-transpose).
    Vec3::new(
        m[0][0] * v[0] + m[1][0] * v[1] + m[2][0] * v[2],
        m[0][1] * v[0] + m[1][1] * v[1] + m[2][1] * v[2],
        m[0][2] * v[0] + m[1][2] * v[1] + m[2][2] * v[2],
    )
}

#[inline]
fn srgb_to_linear(c: f32) -> f32 {
    if c <= 0.04045 {
        c / 12.92
    } else {
        ((c + 0.055) / 1.055).powf(2.4)
    }
}

/// Load a glTF/GLB file into a renderable [`Scene`], framing a camera around it.
pub fn load(path: impl AsRef<std::path::Path>) -> Result<Scene, GltfError> {
    let (doc, buffers, images) = gltf::import(path)?;

    // Convert every material once; primitives index into this table. The final
    // entry is the fallback for primitives with no material.
    let mut materials: Vec<Material> = doc
        .materials()
        .map(|m| convert_material(&m, &images))
        .collect();
    let default_mat = materials.len();
    materials.push(Material::Lambertian {
        albedo: Color::splat(0.7).into(),
        normal: None,
    });

    let mut prims: Vec<Primitive> = Vec::new();
    for scene in doc.scenes() {
        for node in scene.nodes() {
            visit_node(&node, IDENTITY, &buffers, default_mat, &mut prims);
        }
    }
    if prims.is_empty() {
        return Err(GltfError::Empty);
    }

    let camera = frame_camera(&prims);
    Ok(Scene::new(materials, prims, camera, sky_background()))
}

fn visit_node(
    node: &gltf::Node,
    parent: Mat4,
    buffers: &[gltf::buffer::Data],
    default_mat: usize,
    out: &mut Vec<Primitive>,
) {
    let world = mat_mul(&parent, &node.transform().matrix());

    if let Some(mesh) = node.mesh() {
        for prim in mesh.primitives() {
            if prim.mode() != gltf::mesh::Mode::Triangles {
                continue;
            }
            let mat_id = prim.material().index().unwrap_or(default_mat);
            add_primitive(&prim, &world, buffers, mat_id, out);
        }
    }
    for child in node.children() {
        visit_node(&child, world, buffers, default_mat, out);
    }
}

fn add_primitive(
    prim: &gltf::Primitive,
    world: &Mat4,
    buffers: &[gltf::buffer::Data],
    mat_id: MaterialId,
    out: &mut Vec<Primitive>,
) {
    let reader = prim.reader(|b| buffers.get(b.index()).map(|d| d.0.as_slice()));
    let Some(positions) = reader.read_positions() else {
        return;
    };
    let positions: Vec<Vec3> = positions.map(|p| transform_point(world, p)).collect();
    let normals: Option<Vec<Vec3>> = reader
        .read_normals()
        .map(|it| it.map(|n| transform_dir(world, n).normalize()).collect());
    let uvs: Option<Vec<[Float; 2]>> = reader.read_tex_coords(0).map(|tc| tc.into_f32().collect());

    let indices: Vec<u32> = match reader.read_indices() {
        Some(i) => i.into_u32().collect(),
        None => (0..positions.len() as u32).collect(),
    };

    for tri in indices.chunks_exact(3) {
        let (a, b, c) = (tri[0] as usize, tri[1] as usize, tri[2] as usize);
        let mut t = Triangle::new(positions[a], positions[b], positions[c], mat_id);
        if let Some(n) = &normals {
            t = t.with_normals([n[a], n[b], n[c]]);
        }
        if let Some(uv) = &uvs {
            t = t.with_uvs([uv[a], uv[b], uv[c]]);
        }
        out.push(Primitive::from(t));
    }
}

fn convert_material(mat: &gltf::Material, images: &[gltf::image::Data]) -> Material {
    let pbr = mat.pbr_metallic_roughness();
    let base = pbr.base_color_factor();
    let emissive = mat.emissive_factor();
    let metallic = pbr.metallic_factor();
    let roughness = pbr.roughness_factor();

    // A material that is essentially just emissive becomes a light.
    let emissive_max = emissive[0].max(emissive[1]).max(emissive[2]);
    let base_max = base[0].max(base[1]).max(base[2]);
    if emissive_max > 0.01 && base_max < 0.01 {
        return Material::Emissive {
            emit: Color::new(emissive[0], emissive[1], emissive[2]),
        };
    }

    // Base color: a decoded texture if present, else the (linear) factor.
    let albedo = match pbr.base_color_texture() {
        Some(info) => texture_albedo(info.texture().source(), images)
            .unwrap_or_else(|| Color::new(base[0], base[1], base[2]).into()),
        None => Color::new(base[0], base[1], base[2]).into(),
    };

    if metallic > 0.5 {
        Material::Metal {
            albedo,
            roughness: roughness.max(0.03),
            normal: None,
        }
    } else {
        Material::Lambertian {
            albedo,
            normal: None,
        }
    }
}

/// Decode a base-color image into a Prime texture (sRGB→linear). Returns `None`
/// for pixel formats we don't unpack (caller falls back to the base factor).
fn texture_albedo(img: gltf::image::Image, images: &[gltf::image::Data]) -> Option<Texture> {
    let data = images.get(img.index())?;
    let (w, h) = (data.width as usize, data.height as usize);
    let channels = match data.format {
        gltf::image::Format::R8G8B8 => 3,
        gltf::image::Format::R8G8B8A8 => 4,
        _ => return None,
    };
    let mut pixels = Vec::with_capacity(w * h);
    for px in data.pixels.chunks_exact(channels) {
        pixels.push(Color::new(
            srgb_to_linear(px[0] as f32 / 255.0),
            srgb_to_linear(px[1] as f32 / 255.0),
            srgb_to_linear(px[2] as f32 / 255.0),
        ));
    }
    if pixels.len() != w * h {
        return None;
    }
    Some(Texture::Image {
        path: format!("gltf-image-{}", img.index()),
        srgb: true,
        data: Some(Arc::new(ImageTexture::new(w, h, pixels))),
    })
}

/// Frame a camera that sees the whole model (it is auto-oriented, since sample
/// assets rarely ship a useful camera).
fn frame_camera(prims: &[Primitive]) -> CameraConfig {
    let mut lo = Vec3::splat(Float::INFINITY);
    let mut hi = Vec3::splat(Float::NEG_INFINITY);
    for p in prims {
        let b = p.aabb();
        lo = lo.min(b.min);
        hi = hi.max(b.max);
    }
    let center = (lo + hi) * 0.5;
    let radius = ((hi - lo).length() * 0.5).max(1e-3);
    CameraConfig {
        look_from: center + Vec3::new(radius * 0.9, radius * 0.6, radius * 1.9),
        look_at: center,
        vup: Vec3::new(0.0, 1.0, 0.0),
        vfov: 35.0,
        aperture: 0.0,
        focus_dist: None,
    }
}

/// A neutral sky gradient so models are lit (glTF carries no lighting itself).
fn sky_background() -> Background {
    Background::Gradient {
        bottom: Color::new(0.7, 0.72, 0.75),
        top: Color::new(0.35, 0.45, 0.65),
    }
}
