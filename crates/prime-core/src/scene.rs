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

pub struct Scene {
    materials: Vec<Material>,
    bvh: Bvh,
    pub camera: CameraConfig,
    pub background: Background,
}

impl Scene {
    /// Build a scene, constructing the BVH from `primitives`.
    pub fn new(
        materials: Vec<Material>,
        primitives: Vec<Primitive>,
        camera: CameraConfig,
        background: Background,
    ) -> Scene {
        Scene {
            materials,
            bvh: Bvh::build(primitives),
            camera,
            background,
        }
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
