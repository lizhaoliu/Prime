//! The result of a successful ray/primitive intersection.

use crate::math::Vec3;
use crate::ray::Ray;
use crate::{Float, MaterialId};

#[derive(Clone, Copy, Debug)]
pub struct HitRecord {
    /// Ray parameter at the hit.
    pub t: Float,
    /// World-space hit position.
    pub p: Vec3,
    /// Shading normal, always oriented against the incoming ray.
    pub normal: Vec3,
    /// Whether the ray hit the outward-facing side of the surface.
    pub front_face: bool,
    /// Surface texture coordinates.
    pub u: Float,
    pub v: Float,
    /// Index into the scene's material table.
    pub material: MaterialId,
}

impl HitRecord {
    /// Orient `outward_normal` (which must be unit length) to face the ray and
    /// record which side was hit.
    #[inline]
    pub fn with_face_normal(
        ray: &Ray,
        t: Float,
        p: Vec3,
        outward_normal: Vec3,
        u: Float,
        v: Float,
        material: MaterialId,
    ) -> HitRecord {
        let front_face = ray.dir.dot(outward_normal) < 0.0;
        let normal = if front_face {
            outward_normal
        } else {
            -outward_normal
        };
        HitRecord {
            t,
            p,
            normal,
            front_face,
            u,
            v,
            material,
        }
    }
}
