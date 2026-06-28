//! Scene primitives and the sealed [`Primitive`] dispatch enum.
//!
//! Using an `enum` (rather than `Box<dyn Hittable>`) keeps primitives in a
//! flat, cache-friendly array for the BVH and avoids per-hit virtual dispatch.

pub mod sphere;
pub mod triangle;

pub use sphere::Sphere;
pub use triangle::Triangle;

use crate::aabb::Aabb;
use crate::hit::HitRecord;
use crate::ray::Ray;
use crate::{Float, MaterialId};

#[derive(Clone, Copy, Debug)]
pub enum Primitive {
    Sphere(Sphere),
    Triangle(Triangle),
}

impl Primitive {
    #[inline]
    pub fn hit(&self, ray: &Ray, t_min: Float, t_max: Float) -> Option<HitRecord> {
        match self {
            Primitive::Sphere(s) => s.hit(ray, t_min, t_max),
            Primitive::Triangle(t) => t.hit(ray, t_min, t_max),
        }
    }

    #[inline]
    pub fn aabb(&self) -> Aabb {
        match self {
            Primitive::Sphere(s) => s.aabb(),
            Primitive::Triangle(t) => t.aabb(),
        }
    }

    #[inline]
    pub fn centroid(&self) -> crate::math::Vec3 {
        match self {
            Primitive::Sphere(s) => s.centroid(),
            Primitive::Triangle(t) => t.centroid(),
        }
    }

    #[inline]
    pub fn material(&self) -> MaterialId {
        match self {
            Primitive::Sphere(s) => s.material,
            Primitive::Triangle(t) => t.material,
        }
    }
}

impl From<Sphere> for Primitive {
    fn from(s: Sphere) -> Self {
        Primitive::Sphere(s)
    }
}

impl From<Triangle> for Primitive {
    fn from(t: Triangle) -> Self {
        Primitive::Triangle(t)
    }
}
