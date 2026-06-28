//! The renderable scene: a material table, a BVH of primitives, a camera
//! configuration, and a background.

use crate::bvh::Bvh;
use crate::camera::CameraConfig;
use crate::geometry::Primitive;
use crate::hit::HitRecord;
use crate::material::Material;
use crate::math::Vec3;
use crate::ray::Ray;
use crate::{Color, Float, MaterialId};
use rand::Rng;

/// What a ray sees when it escapes the scene.
#[cfg_attr(feature = "serde", derive(serde::Serialize, serde::Deserialize))]
#[derive(Clone, Copy, Debug)]
pub enum Background {
    /// Constant color in every direction.
    Solid(Color),
    /// Vertical gradient between `bottom` (looking down) and `top` (looking up).
    Gradient { bottom: Color, top: Color },
}

impl Background {
    /// Sample the background along `dir` (the escaping ray direction).
    #[inline]
    pub fn sample(&self, dir: Vec3) -> Color {
        match self {
            Background::Solid(c) => *c,
            Background::Gradient { bottom, top } => {
                let t = 0.5 * (dir.normalize().y + 1.0);
                *bottom * (1.0 - t) + *top * t
            }
        }
    }
}

impl Default for Background {
    fn default() -> Self {
        Background::Gradient {
            bottom: Color::ONE,
            top: Color::new(0.5, 0.7, 1.0),
        }
    }
}

/// An emissive primitive, retained for direct light sampling (NEE).
struct Light {
    prim: Primitive,
    emit: Color,
    area: Float,
}

/// The result of sampling a light: a direction toward it, the distance to the
/// sampled point, the solid-angle pdf, and the light's emitted radiance.
pub struct LightSample {
    pub wi: Vec3,
    pub dist: Float,
    pub pdf: Float,
    pub emit: Color,
}

pub struct Scene {
    materials: Vec<Material>,
    bvh: Bvh,
    lights: Vec<Light>,
    pub camera: CameraConfig,
    pub background: Background,
}

impl Scene {
    /// Build a scene, constructing the BVH from `primitives` and collecting the
    /// emissive ones into a light list for direct sampling.
    pub fn new(
        materials: Vec<Material>,
        primitives: Vec<Primitive>,
        camera: CameraConfig,
        background: Background,
    ) -> Scene {
        let lights = primitives
            .iter()
            .filter_map(|p| {
                let emit = materials[p.material()].emitted();
                (emit.max_component() > 0.0).then(|| Light {
                    prim: *p,
                    emit,
                    area: p.area(),
                })
            })
            .collect();

        Scene {
            materials,
            bvh: Bvh::build(primitives),
            lights,
            camera,
            background,
        }
    }

    pub fn num_lights(&self) -> usize {
        self.lights.len()
    }

    /// Pick a light uniformly and sample a point on it, returning a direction
    /// and the solid-angle pdf for connecting the shading point `p` to it.
    pub fn sample_light<R: Rng + ?Sized>(&self, p: Vec3, rng: &mut R) -> Option<LightSample> {
        let n = self.lights.len();
        if n == 0 {
            return None;
        }
        let light = &self.lights[rng.gen_range(0..n)];
        let (q, n_light) = light.prim.sample(rng);
        let d = q - p;
        let dist2 = d.length_squared();
        if dist2 < 1e-8 {
            return None;
        }
        let dist = dist2.sqrt();
        let wi = d / dist;
        // Two-sided emitters: use the absolute cosine at the light.
        let cos_light = n_light.dot(wi).abs();
        if cos_light < 1e-6 {
            return None;
        }
        let pdf = dist2 / (n as Float * light.area * cos_light);
        Some(LightSample {
            wi,
            dist,
            pdf,
            emit: light.emit,
        })
    }

    /// Solid-angle pdf that [`Scene::sample_light`] would assign to a path ray
    /// (travelling along `dir`) that landed on the emitter described by `hit`.
    /// Used for the MIS weight when a BSDF-sampled ray hits a light.
    pub fn light_pdf(&self, dir: Vec3, hit: &HitRecord) -> Float {
        let n = self.lights.len();
        if n == 0 || hit.area <= 0.0 {
            return 0.0;
        }
        let cos = hit.normal.dot(dir).abs();
        if cos < 1e-6 {
            return 0.0;
        }
        (hit.t * hit.t) / (n as Float * hit.area * cos)
    }

    /// Is anything between `origin` and `origin + dir * t_max` (within
    /// `[t_min, t_max]`)? Used for shadow rays.
    pub fn occluded(&self, origin: Vec3, dir: Vec3, t_min: Float, t_max: Float) -> bool {
        self.bvh.occluded(&Ray::new(origin, dir), t_min, t_max)
    }

    #[inline]
    pub fn material(&self, id: MaterialId) -> &Material {
        &self.materials[id]
    }

    #[inline]
    pub fn hit(&self, ray: &Ray, t_min: Float, t_max: Float) -> Option<HitRecord> {
        self.bvh.hit(ray, t_min, t_max)
    }

    pub fn primitive_count(&self) -> usize {
        self.bvh.len()
    }

    pub fn material_count(&self) -> usize {
        self.materials.len()
    }
}
