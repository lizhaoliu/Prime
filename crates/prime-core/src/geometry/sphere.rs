//! Analytic sphere primitive. Spheres make compact, exact test scenes
//! (e.g. Cornell-box demos) without needing external mesh assets.

use crate::aabb::Aabb;
use crate::hit::HitRecord;
use crate::math::sampling::random_unit_vector;
use crate::math::Vec3;
use crate::ray::Ray;
use crate::sampler::Sampler;
use crate::{Float, MaterialId};
use std::f32::consts::PI;

#[derive(Clone, Copy, Debug)]
pub struct Sphere {
    pub center: Vec3,
    pub radius: Float,
    pub material: MaterialId,
}

impl Sphere {
    pub fn new(center: Vec3, radius: Float, material: MaterialId) -> Self {
        Sphere {
            center,
            radius,
            material,
        }
    }

    pub fn hit(&self, ray: &Ray, t_min: Float, t_max: Float) -> Option<HitRecord> {
        let oc = ray.origin - self.center;
        let a = ray.dir.length_squared();
        let half_b = oc.dot(ray.dir);
        let c = oc.length_squared() - self.radius * self.radius;
        let disc = half_b * half_b - a * c;
        if disc < 0.0 {
            return None;
        }
        let sqrt_d = disc.sqrt();

        // Nearest root within the valid interval.
        let mut root = (-half_b - sqrt_d) / a;
        if root < t_min || root > t_max {
            root = (-half_b + sqrt_d) / a;
            if root < t_min || root > t_max {
                return None;
            }
        }

        let p = ray.at(root);
        let outward = (p - self.center) * (1.0 / self.radius);
        let (u, v) = sphere_uv(outward);
        let mut hit =
            HitRecord::with_face_normal(ray, root, p, outward, u, v, self.area(), self.material);
        // Tangent along increasing longitude (dP/dφ), for normal mapping.
        let tang = Vec3::new(-outward.z, 0.0, outward.x);
        if tang.length_squared() > 1e-12 {
            hit.tangent = tang.normalize();
        }
        Some(hit)
    }

    pub fn aabb(&self) -> Aabb {
        let r = Vec3::splat(self.radius);
        Aabb::new(self.center - r, self.center + r)
    }

    pub fn centroid(&self) -> Vec3 {
        self.center
    }

    #[inline]
    pub fn area(&self) -> Float {
        4.0 * PI * self.radius * self.radius
    }

    /// Uniformly sample a point on the sphere surface, returning the point and
    /// its outward normal.
    pub fn sample(&self, sampler: &mut Sampler) -> (Vec3, Vec3) {
        let dir = random_unit_vector(sampler);
        (self.center + dir * self.radius, dir)
    }
}

/// Map a point on the unit sphere to `(u, v)` in `[0, 1]`.
fn sphere_uv(p: Vec3) -> (Float, Float) {
    let theta = (-p.y).acos();
    let phi = (-p.z).atan2(p.x) + PI;
    (phi / (2.0 * PI), theta / PI)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ray_hits_unit_sphere_at_front() {
        let s = Sphere::new(Vec3::ZERO, 1.0, 0);
        let r = Ray::new(Vec3::new(0.0, 0.0, -5.0), Vec3::new(0.0, 0.0, 1.0));
        let h = s.hit(&r, 0.001, Float::INFINITY).expect("should hit");
        assert!((h.t - 4.0).abs() < 1e-4);
        assert!(h.front_face);
        assert!((h.normal - Vec3::new(0.0, 0.0, -1.0)).length() < 1e-4);
    }

    #[test]
    fn ray_from_inside_hits_far_wall_with_flipped_normal() {
        let s = Sphere::new(Vec3::ZERO, 1.0, 0);
        let r = Ray::new(Vec3::ZERO, Vec3::new(0.0, 0.0, 1.0));
        let h = s
            .hit(&r, 0.001, Float::INFINITY)
            .expect("should hit from inside");
        assert!((h.t - 1.0).abs() < 1e-4);
        assert!(!h.front_face);
        // Normal points back toward the ray origin (inward).
        assert!(h.normal.dot(r.dir) < 0.0);
    }
}
