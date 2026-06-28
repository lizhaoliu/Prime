//! Surface scattering models (BSDFs).
//!
//! The legacy design had an abstract `Material` base with `IdealDiffuseModel` /
//! `IdealSpecularModel` subclasses, a never-implemented transmission path, and
//! dead branches in the specular BRDF. Here the closed set of models is a
//! single `enum` — a true sealed hierarchy that the compiler can exhaustively
//! check and dispatch without virtual calls — and dielectric transmission is
//! actually implemented.

use crate::hit::HitRecord;
use crate::math::sampling::{cosine_weighted_hemisphere, random_unit_vector};
use crate::math::Vec3;
use crate::ray::Ray;
use crate::{Color, Float};
use rand::Rng;

#[cfg_attr(feature = "serde", derive(serde::Serialize, serde::Deserialize))]
#[derive(Clone, Copy, Debug)]
pub enum Material {
    /// Ideal diffuse reflector (cosine-weighted importance sampled).
    Lambertian { albedo: Color },
    /// Glossy/mirror reflector; `fuzz` in `[0, 1]` blurs the reflection.
    Metal { albedo: Color, fuzz: Float },
    /// Smooth dielectric (glass/water) with refraction + Fresnel reflection.
    Dielectric { ior: Float },
    /// Light source: emits radiance, does not scatter.
    Emissive { emit: Color },
}

/// A scattered ray direction and the throughput weight to apply.
pub struct Scatter {
    pub attenuation: Color,
    pub direction: Vec3,
}

impl Material {
    /// Radiance emitted by this surface (zero for non-emitters).
    #[inline]
    pub fn emitted(&self) -> Color {
        match self {
            Material::Emissive { emit } => *emit,
            _ => Color::ZERO,
        }
    }

    /// Sample an outgoing direction and its throughput weight, or `None` if the
    /// ray is absorbed.
    pub fn scatter<R: Rng + ?Sized>(
        &self,
        ray_in: &Ray,
        hit: &HitRecord,
        rng: &mut R,
    ) -> Option<Scatter> {
        match *self {
            Material::Lambertian { albedo } => {
                // Cosine-weighted sampling makes the pdf cancel the cosine term,
                // so the throughput is simply the albedo.
                let mut dir = cosine_weighted_hemisphere(rng, hit.normal);
                if dir.is_near_zero() {
                    dir = hit.normal;
                }
                Some(Scatter {
                    attenuation: albedo,
                    direction: dir.normalize(),
                })
            }

            Material::Metal { albedo, fuzz } => {
                let reflected = ray_in.dir.normalize().reflect(hit.normal);
                let dir = reflected + random_unit_vector(rng) * fuzz.clamp(0.0, 1.0);
                if dir.dot(hit.normal) <= 0.0 {
                    return None; // scattered below the surface
                }
                Some(Scatter {
                    attenuation: albedo,
                    direction: dir.normalize(),
                })
            }

            Material::Dielectric { ior } => {
                let eta_ratio = if hit.front_face { 1.0 / ior } else { ior };
                let unit_dir = ray_in.dir.normalize();
                let cos_theta = (-unit_dir).dot(hit.normal).min(1.0);
                let sin_theta = (1.0 - cos_theta * cos_theta).max(0.0).sqrt();

                let cannot_refract = eta_ratio * sin_theta > 1.0;
                let dir = if cannot_refract
                    || schlick_reflectance(cos_theta, eta_ratio) > rng.gen::<Float>()
                {
                    unit_dir.reflect(hit.normal)
                } else {
                    unit_dir.refract(hit.normal, eta_ratio)
                };
                Some(Scatter {
                    attenuation: Color::ONE,
                    direction: dir.normalize(),
                })
            }

            Material::Emissive { .. } => None,
        }
    }
}

/// Schlick's polynomial approximation of the Fresnel reflectance.
#[inline]
fn schlick_reflectance(cosine: Float, eta_ratio: Float) -> Float {
    let r0 = ((1.0 - eta_ratio) / (1.0 + eta_ratio)).powi(2);
    r0 + (1.0 - r0) * (1.0 - cosine).powi(5)
}

#[cfg(test)]
mod tests {
    use super::*;
    use rand::rngs::SmallRng;
    use rand::SeedableRng;

    fn flat_hit(front_face: bool) -> HitRecord {
        HitRecord {
            t: 1.0,
            p: Vec3::ZERO,
            normal: Vec3::new(0.0, 1.0, 0.0),
            front_face,
            u: 0.0,
            v: 0.0,
            material: 0,
        }
    }

    #[test]
    fn lambertian_scatters_into_hemisphere() {
        let mut rng = SmallRng::seed_from_u64(1);
        let m = Material::Lambertian {
            albedo: Color::new(0.5, 0.5, 0.5),
        };
        let ray = Ray::new(Vec3::new(0.0, 2.0, 0.0), Vec3::new(0.0, -1.0, 0.0));
        for _ in 0..1000 {
            let s = m.scatter(&ray, &flat_hit(true), &mut rng).unwrap();
            assert!(s.direction.dot(Vec3::new(0.0, 1.0, 0.0)) >= -1e-3);
            assert_eq!(s.attenuation, Color::new(0.5, 0.5, 0.5));
        }
    }

    #[test]
    fn emissive_does_not_scatter_but_emits() {
        let mut rng = SmallRng::seed_from_u64(2);
        let m = Material::Emissive {
            emit: Color::new(4.0, 4.0, 4.0),
        };
        let ray = Ray::new(Vec3::ZERO, Vec3::new(0.0, -1.0, 0.0));
        assert!(m.scatter(&ray, &flat_hit(true), &mut rng).is_none());
        assert_eq!(m.emitted(), Color::new(4.0, 4.0, 4.0));
    }

    #[test]
    fn metal_reflects_mirror_direction() {
        let mut rng = SmallRng::seed_from_u64(3);
        let m = Material::Metal {
            albedo: Color::ONE,
            fuzz: 0.0,
        };
        // Incoming down-and-right, flat ground -> reflect up-and-right.
        let ray = Ray::new(Vec3::ZERO, Vec3::new(1.0, -1.0, 0.0).normalize());
        let s = m.scatter(&ray, &flat_hit(true), &mut rng).unwrap();
        let expected = Vec3::new(1.0, 1.0, 0.0).normalize();
        assert!((s.direction - expected).length() < 1e-4);
    }
}
