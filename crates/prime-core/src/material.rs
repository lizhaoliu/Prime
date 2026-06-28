//! Surface scattering models (BSDFs).
//!
//! The legacy design had an abstract `Material` base with `IdealDiffuseModel` /
//! `IdealSpecularModel` subclasses, a never-implemented transmission path, and
//! dead branches in the specular BRDF. Here the closed set of models is a
//! single `enum` — a true sealed hierarchy the compiler exhaustively checks and
//! dispatches without virtual calls — and dielectric transmission is actually
//! implemented.
//!
//! The interface is designed for an integrator that does **multiple importance
//! sampling**, so each model exposes three operations:
//!
//! * [`Material::sample`] — importance-sample an outgoing direction;
//! * [`Material::eval`] — evaluate the BSDF `f(wo, wi)` for a given pair;
//! * [`Material::pdf`] — the solid-angle pdf that `sample` would assign to `wi`.
//!
//! Direction convention: `wo` points from the surface toward the viewer
//! (`-ray.dir`) and `wi` points from the surface toward the light / next
//! vertex. The shading normal `hit.normal` is always oriented to face `wo`.

use crate::hit::HitRecord;
use crate::math::sampling::{cosine_weighted_hemisphere, random_unit_vector};
use crate::math::Vec3;
use crate::{Color, Float};
use rand::Rng;
use std::f32::consts::FRAC_1_PI;

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

/// A sampled scattering direction with its BSDF value and pdf.
pub struct BsdfSample {
    /// Sampled direction (unit, world space).
    pub wi: Vec3,
    /// BSDF value `f(wo, wi)`. For a specular sample this is the full throughput
    /// weight (no cosine/pdf factors are applied by the caller).
    pub f: Color,
    /// Solid-angle pdf of `wi`. Meaningless (and ignored) when `specular`.
    pub pdf: Float,
    /// True for delta BSDFs (perfect mirror / glass): no light sampling applies,
    /// and the caller multiplies throughput by `f` directly.
    pub specular: bool,
}

impl Material {
    /// Radiance emitted by this surface (zero for non-emitters). Emitters are
    /// treated as two-sided.
    #[inline]
    pub fn emitted(&self) -> Color {
        match self {
            Material::Emissive { emit } => *emit,
            _ => Color::ZERO,
        }
    }

    /// Whether this is a delta (perfectly specular) BSDF, which cannot be
    /// connected to via direct light sampling.
    #[inline]
    pub fn is_specular(&self) -> bool {
        matches!(self, Material::Metal { .. } | Material::Dielectric { .. })
    }

    /// Importance-sample an outgoing direction, or `None` if absorbed.
    pub fn sample<R: Rng + ?Sized>(
        &self,
        wo: Vec3,
        hit: &HitRecord,
        rng: &mut R,
    ) -> Option<BsdfSample> {
        let n = hit.normal;
        match *self {
            Material::Lambertian { albedo } => {
                let mut wi = cosine_weighted_hemisphere(rng, n);
                if wi.is_near_zero() {
                    wi = n;
                }
                let wi = wi.normalize();
                let cos = wi.dot(n);
                if cos <= 0.0 {
                    return None;
                }
                Some(BsdfSample {
                    wi,
                    f: albedo * FRAC_1_PI,
                    pdf: cos * FRAC_1_PI,
                    specular: false,
                })
            }

            Material::Metal { albedo, fuzz } => {
                let reflected = (-wo).reflect(n);
                let wi = (reflected + random_unit_vector(rng) * fuzz.clamp(0.0, 1.0)).normalize();
                if wi.dot(n) <= 0.0 {
                    return None; // scattered below the surface
                }
                Some(BsdfSample {
                    wi,
                    f: albedo,
                    pdf: 1.0,
                    specular: true,
                })
            }

            Material::Dielectric { ior } => {
                let eta_ratio = if hit.front_face { 1.0 / ior } else { ior };
                let incoming = -wo; // == ray.dir
                let cos_theta = wo.dot(n).min(1.0);
                let sin_theta = (1.0 - cos_theta * cos_theta).max(0.0).sqrt();
                let cannot_refract = eta_ratio * sin_theta > 1.0;
                let wi = if cannot_refract
                    || schlick_reflectance(cos_theta, eta_ratio) > rng.gen::<Float>()
                {
                    incoming.reflect(n)
                } else {
                    incoming.refract(n, eta_ratio)
                };
                Some(BsdfSample {
                    wi: wi.normalize(),
                    f: Color::ONE,
                    pdf: 1.0,
                    specular: true,
                })
            }

            Material::Emissive { .. } => None,
        }
    }

    /// Evaluate the BSDF `f(wo, wi)`. Zero for specular materials (their
    /// contribution is a delta handled by [`Material::sample`]).
    pub fn eval(&self, wo: Vec3, wi: Vec3, hit: &HitRecord) -> Color {
        match *self {
            Material::Lambertian { albedo } => {
                let n = hit.normal;
                if wi.dot(n) > 0.0 && wo.dot(n) > 0.0 {
                    albedo * FRAC_1_PI
                } else {
                    Color::ZERO
                }
            }
            _ => Color::ZERO,
        }
    }

    /// Solid-angle pdf that [`Material::sample`] would assign to `wi`. Zero for
    /// specular materials.
    pub fn pdf(&self, _wo: Vec3, wi: Vec3, hit: &HitRecord) -> Float {
        match self {
            Material::Lambertian { .. } => {
                let cos = wi.dot(hit.normal);
                if cos > 0.0 {
                    cos * FRAC_1_PI
                } else {
                    0.0
                }
            }
            _ => 0.0,
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
            area: 1.0,
            material: 0,
        }
    }

    #[test]
    fn lambertian_sample_eval_pdf_are_consistent() {
        let mut rng = SmallRng::seed_from_u64(1);
        let albedo = Color::new(0.5, 0.4, 0.3);
        let m = Material::Lambertian { albedo };
        let hit = flat_hit(true);
        let wo = Vec3::new(0.0, 1.0, 0.0);
        for _ in 0..1000 {
            let s = m.sample(wo, &hit, &mut rng).unwrap();
            assert!(s.direction_in_hemisphere(hit.normal));
            // sample() and eval()/pdf() must agree for the sampled direction.
            assert!((s.f - m.eval(wo, s.wi, &hit)).length() < 1e-5);
            assert!((s.pdf - m.pdf(wo, s.wi, &hit)).abs() < 1e-5);
            assert!(!s.specular);
        }
    }

    #[test]
    fn emissive_does_not_scatter_but_emits() {
        let mut rng = SmallRng::seed_from_u64(2);
        let m = Material::Emissive {
            emit: Color::new(4.0, 4.0, 4.0),
        };
        assert!(m.sample(Vec3::new(0.0, 1.0, 0.0), &flat_hit(true), &mut rng).is_none());
        assert_eq!(m.emitted(), Color::new(4.0, 4.0, 4.0));
        assert!(!m.is_specular());
    }

    #[test]
    fn metal_reflects_mirror_direction() {
        let mut rng = SmallRng::seed_from_u64(3);
        let m = Material::Metal {
            albedo: Color::ONE,
            fuzz: 0.0,
        };
        // wo points up-and-left toward the viewer; mirror wi is up-and-right.
        let wo = Vec3::new(-1.0, 1.0, 0.0).normalize();
        let s = m.sample(wo, &flat_hit(true), &mut rng).unwrap();
        assert!(s.specular);
        let expected = Vec3::new(1.0, 1.0, 0.0).normalize();
        assert!((s.wi - expected).length() < 1e-4);
    }

    #[test]
    fn dielectric_is_specular_and_white() {
        let mut rng = SmallRng::seed_from_u64(4);
        let m = Material::Dielectric { ior: 1.5 };
        let s = m
            .sample(Vec3::new(0.0, 1.0, 0.0), &flat_hit(true), &mut rng)
            .unwrap();
        assert!(s.specular);
        assert_eq!(s.f, Color::ONE);
    }

    impl BsdfSample {
        fn direction_in_hemisphere(&self, n: Vec3) -> bool {
            self.wi.dot(n) >= -1e-4
        }
    }
}
