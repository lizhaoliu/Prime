//! Scene resolution for the server: built-in names, `.ron` files, or `.obj`
//! meshes (auto-framed). Mirrors the CLI's resolver; kept here because Cargo
//! binaries can't depend on one another.

use std::path::Path;

use anyhow::{bail, Context, Result};
use prime_core::aabb::Aabb;
use prime_core::camera::CameraConfig;
use prime_core::desc::SceneDesc;
use prime_core::geometry::Primitive;
use prime_core::material::Material;
use prime_core::math::Vec3;
use prime_core::scene::{Background, Scene};
use prime_core::texture::ImageData;
use prime_core::{demo, obj, Color, Float};

/// Resolve a scene source into a [`Scene`]. `aspect` is used only to auto-frame
/// bare OBJ meshes.
pub fn resolve(source: &str, aspect: Float) -> Result<Scene> {
    match source {
        "showcase" => return Ok(demo::showcase()),
        "cornell" => return Ok(demo::cornell_box()),
        "spheres" => return Ok(demo::spheres()),
        "rtweekend" => return Ok(demo::rtweekend()),
        "studio" => return Ok(demo::studio()),
        "sky" => return Ok(demo::sky()),
        "textured" => return Ok(demo::textured()),
        _ => {}
    }

    let path = Path::new(source);
    match path.extension().and_then(|e| e.to_str()) {
        Some("ron") => {
            let text = std::fs::read_to_string(path)
                .with_context(|| format!("reading scene file {}", path.display()))?;
            let desc: SceneDesc = ron::from_str(&text).context("parsing RON scene")?;
            let base_dir = path.parent().unwrap_or_else(|| Path::new("."));
            Ok(desc
                .build(base_dir, &mut decode_image)
                .context("building scene")?)
        }
        Some("obj") => load_obj_scene(path, aspect),
        _ => bail!(
            "unknown scene '{source}': expected a built-in name (showcase, studio, \
             rtweekend, sky, textured, cornell, spheres), a .ron scene, or a .obj mesh"
        ),
    }
}

/// Decode an image file into RGB pixels for textures.
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

fn load_obj_scene(path: &Path, aspect: Float) -> Result<Scene> {
    let material = Material::Lambertian {
        albedo: Color::new(0.73, 0.73, 0.73).into(),
        normal: None,
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

/// Frame an axis-aligned box from a raised three-quarter view, fitting the
/// bounding sphere to whichever FOV axis is tighter (with a modest zoom-in).
fn frame_camera(bounds: Aabb, aspect: Float) -> CameraConfig {
    let center = bounds.centroid();
    let radius = ((bounds.max - bounds.min).length() * 0.5).max(1e-3);
    let vfov: Float = 40.0;
    let vfov_rad = vfov.to_radians();
    let hfov_rad = 2.0 * (aspect * (vfov_rad * 0.5).tan()).atan();
    let half_fov = 0.5 * vfov_rad.min(hfov_rad);
    let fit_dist = radius / half_fov.sin();
    let dir = Vec3::new(0.35, 0.28, 1.0).normalize();
    CameraConfig {
        look_from: center + dir * fit_dist * 0.62,
        look_at: center,
        vup: Vec3::new(0.0, 1.0, 0.0),
        vfov,
        aperture: 0.0,
        focus_dist: None,
    }
}
