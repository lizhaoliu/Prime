//! Surface scattering models (BSDFs).
//!
//! The legacy design had an abstract `Material` base with `IdealDiffuseModel` /
//! `IdealSpecularModel` subclasses, a never-implemented transmission path, an
//! unused `MicrofacetDistribution` stub, and dead branches in the specular
//! BRDF. Here the closed set of models is a single `enum` — a true sealed
//! hierarchy the compiler exhaustively checks and dispatches without virtual
//! calls — dielectric transmission is implemented, and the microfacet stub is
//! realized as a GGX conductor.
//!
//! Each model exposes the three operations a multiple-importance-sampling
//! integrator needs:
//!
//! * [`Material::sample`] — importance-sample an outgoing direction;
//! * [`Material::eval`] — evaluate the BSDF `f(wo, wi)`;
//! * [`Material::pdf`] — the solid-angle pdf that `sample` would assign to `wi`.
//!
//! Direction convention: `wo` points from the surface toward the viewer
//! (`-ray.dir`) and `wi` points from the surface toward the light / next
//! vertex. The shading normal `hit.normal` is always oriented to face `wo`.

use crate::hit::HitRecord;
use crate::math::sampling::cosine_weighted_hemisphere;
use crate::math::{Onb, Vec3};
use crate::sampler::Sampler;
use crate::texture::{ImageData, Texture};
use crate::{Color, Float};
use std::f32::consts::{FRAC_1_PI, PI};
use std::path::Path;

/// Below this roughness a [`Material::Metal`] is treated as a perfect mirror
/// (a delta BSDF), avoiding the numerical spike of a near-singular GGX lobe.
const MIRROR_ROUGHNESS: Float = 0.02;

#[cfg_attr(feature = "serde", derive(serde::Serialize, serde::Deserialize))]
#[derive(Clone, Debug)]
pub enum Material {
    /// Ideal diffuse reflector (cosine-weighted importance sampled).
    Lambertian { albedo: Texture },
    /// GGX microfacet conductor. `roughness` in `[0, 1]`: 0 is a perfect
    /// mirror, higher values are rougher (more blurred reflections).
    Metal { albedo: Texture, roughness: Float },
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
        match self {
            Material::Metal { roughness, .. } => *roughness <= MIRROR_ROUGHNESS,
            Material::Dielectric { .. } => true,
            _ => false,
        }
    }

    /// Importance-sample an outgoing direction, or `None` if absorbed.
    pub fn sample(&self, wo: Vec3, hit: &HitRecord, sampler: &mut Sampler) -> Option<BsdfSample> {
        let n = hit.normal;
        match self {
            Material::Lambertian { albedo } => {
                let mut wi = cosine_weighted_hemisphere(sampler, n);
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
                    f: albedo.sample(hit.u, hit.v) * FRAC_1_PI,
                    pdf: cos * FRAC_1_PI,
                    specular: false,
                })
            }

            Material::Metal { albedo, roughness } => {
                let roughness = *roughness;
                if roughness <= MIRROR_ROUGHNESS {
                    // Perfect mirror: a delta BSDF.
                    let wi = (-wo).reflect(n);
                    if wi.dot(n) <= 0.0 {
                        return None;
                    }
                    return Some(BsdfSample {
                        wi: wi.normalize(),
                        f: albedo.sample(hit.u, hit.v),
                        pdf: 1.0,
                        specular: true,
                    });
                }
                // Rough conductor: sample a microfacet normal via the GGX VNDF,
                // reflect about it, then reuse eval()/pdf() so the three stay
                // consistent.
                let a = ggx_alpha(roughness);
                let onb = Onb::from_w(n);
                let wo_l = Vec3::new(wo.dot(onb.u), wo.dot(onb.v), wo.dot(onb.w));
                if wo_l.z <= 0.0 {
                    return None;
                }
                let h = onb.local(sample_ggx_vndf(wo_l, a, sampler));
                let wi = (-wo).reflect(h);
                if wi.dot(n) <= 0.0 {
                    return None;
                }
                let pdf = self.pdf(wo, wi, hit);
                if pdf <= 0.0 {
                    return None;
                }
                Some(BsdfSample {
                    wi,
                    f: self.eval(wo, wi, hit),
                    pdf,
                    specular: false,
                })
            }

            Material::Dielectric { ior } => {
                let ior = *ior;
                let eta_ratio = if hit.front_face { 1.0 / ior } else { ior };
                let incoming = -wo; // == ray.dir
                let cos_theta = wo.dot(n).min(1.0);
                let sin_theta = (1.0 - cos_theta * cos_theta).max(0.0).sqrt();
                let cannot_refract = eta_ratio * sin_theta > 1.0;
                let wi = if cannot_refract
                    || schlick_reflectance(cos_theta, eta_ratio) > sampler.next_1d()
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
        let n = hit.normal;
        match self {
            Material::Lambertian { albedo } => {
                if wi.dot(n) > 0.0 && wo.dot(n) > 0.0 {
                    albedo.sample(hit.u, hit.v) * FRAC_1_PI
                } else {
                    Color::ZERO
                }
            }
            Material::Metal { albedo, roughness } => {
                let roughness = *roughness;
                if roughness <= MIRROR_ROUGHNESS {
                    return Color::ZERO;
                }
                let no = wo.dot(n);
                let nl = wi.dot(n);
                if no <= 0.0 || nl <= 0.0 {
                    return Color::ZERO;
                }
                let h = (wo + wi).normalize();
                let nh = n.dot(h).max(0.0);
                let vh = wo.dot(h).max(0.0);
                let a2 = ggx_alpha(roughness).powi(2);
                let d = ggx_d(nh, a2);
                let g = smith_g2(no, nl, a2);
                let fr = fresnel_schlick(vh, albedo.sample(hit.u, hit.v));
                fr * (d * g / (4.0 * no * nl))
            }
            _ => Color::ZERO,
        }
    }

    /// Solid-angle pdf that [`Material::sample`] would assign to `wi`. Zero for
    /// specular materials.
    pub fn pdf(&self, wo: Vec3, wi: Vec3, hit: &HitRecord) -> Float {
        let n = hit.normal;
        match self {
            Material::Lambertian { .. } => {
                let cos = wi.dot(n);
                if cos > 0.0 {
                    cos * FRAC_1_PI
                } else {
                    0.0
                }
            }
            Material::Metal { roughness, .. } => {
                let roughness = *roughness;
                if roughness <= MIRROR_ROUGHNESS {
                    return 0.0;
                }
                let no = wo.dot(n);
                let nl = wi.dot(n);
                if no <= 0.0 || nl <= 0.0 {
                    return 0.0;
                }
                let h = (wo + wi).normalize();
                let nh = n.dot(h).max(0.0);
                let a2 = ggx_alpha(roughness).powi(2);
                // VNDF pdf: D(h) * G1(wo) / (4 * NdotV).
                ggx_d(nh, a2) * smith_g1(no, a2) / (4.0 * no)
            }
            _ => 0.0,
        }
    }

    /// Resolve any image textures (via a front-end decoder) relative to
    /// `base_dir`. No-op for procedural/constant parameters.
    pub fn resolve_textures<F>(&mut self, base_dir: &Path, decoder: &mut F) -> Result<(), String>
    where
        F: FnMut(&Path) -> Result<ImageData, String>,
    {
        match self {
            Material::Lambertian { albedo } => albedo.resolve(base_dir, decoder),
            Material::Metal { albedo, .. } => albedo.resolve(base_dir, decoder),
            _ => Ok(()),
        }
    }
}

// --- GGX microfacet helpers -------------------------------------------------

/// Map user roughness to the GGX `α` width parameter (clamped away from the
/// singular mirror limit).
#[inline]
fn ggx_alpha(roughness: Float) -> Float {
    roughness.clamp(1e-3, 1.0)
}

/// GGX (Trowbridge-Reitz) normal distribution, `a2 = α²`.
#[inline]
fn ggx_d(n_dot_h: Float, a2: Float) -> Float {
    let x = n_dot_h * n_dot_h * (a2 - 1.0) + 1.0;
    a2 / (PI * x * x)
}

/// Smith masking-shadowing Λ term for the GGX distribution.
#[inline]
fn smith_lambda(cos: Float, a2: Float) -> Float {
    let c2 = (cos * cos).max(1e-8);
    let tan2 = (1.0 - c2) / c2;
    0.5 * (-1.0 + (1.0 + a2 * tan2).sqrt())
}

#[inline]
fn smith_g1(cos: Float, a2: Float) -> Float {
    1.0 / (1.0 + smith_lambda(cos, a2))
}

/// Height-correlated Smith masking-shadowing for a (wo, wi) pair.
#[inline]
fn smith_g2(cos_o: Float, cos_l: Float, a2: Float) -> Float {
    1.0 / (1.0 + smith_lambda(cos_o, a2) + smith_lambda(cos_l, a2))
}

/// Schlick Fresnel for a conductor whose normal-incidence reflectance is `f0`.
#[inline]
fn fresnel_schlick(cos: Float, f0: Color) -> Color {
    let m = (1.0 - cos).clamp(0.0, 1.0);
    let m5 = m * m * m * m * m;
    f0 + (Color::ONE - f0) * m5
}

/// Sample the GGX distribution of visible normals (Heitz 2018), isotropic.
/// `ve` is the view direction in the local frame (z = surface normal); returns
/// a microfacet normal in the same local frame.
fn sample_ggx_vndf(ve: Vec3, alpha: Float, sampler: &mut Sampler) -> Vec3 {
    // Transform the view direction to the hemisphere configuration.
    let vh = Vec3::new(alpha * ve.x, alpha * ve.y, ve.z).normalize();
    // Orthonormal basis around vh.
    let lensq = vh.x * vh.x + vh.y * vh.y;
    let t1 = if lensq > 1e-12 {
        Vec3::new(-vh.y, vh.x, 0.0) * (1.0 / lensq.sqrt())
    } else {
        Vec3::new(1.0, 0.0, 0.0)
    };
    let t2 = vh.cross(t1);
    // Sample a point on the projected disk.
    let (u1, u2) = sampler.next_2d();
    let r = u1.sqrt();
    let phi = 2.0 * PI * u2;
    let p1 = r * phi.cos();
    let mut p2 = r * phi.sin();
    let s = 0.5 * (1.0 + vh.z);
    p2 = (1.0 - s) * (1.0 - p1 * p1).max(0.0).sqrt() + s * p2;
    let pz = (1.0 - p1 * p1 - p2 * p2).max(0.0).sqrt();
    let nh = t1 * p1 + t2 * p2 + vh * pz;
    // Transform back to the ellipsoid configuration.
    Vec3::new(alpha * nh.x, alpha * nh.y, nh.z.max(0.0)).normalize()
}

/// Schlick's polynomial approximation of the Fresnel reflectance (dielectric).
#[inline]
fn schlick_reflectance(cosine: Float, eta_ratio: Float) -> Float {
    let r0 = ((1.0 - eta_ratio) / (1.0 + eta_ratio)).powi(2);
    r0 + (1.0 - r0) * (1.0 - cosine).powi(5)
}

#[cfg(test)]
mod tests {
    use super::*;

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
        let mut sampler = Sampler::random(1);
        let albedo = Color::new(0.5, 0.4, 0.3);
        let m = Material::Lambertian {
            albedo: albedo.into(),
        };
        let hit = flat_hit(true);
        let wo = Vec3::new(0.0, 1.0, 0.0);
        for _ in 0..1000 {
            let s = m.sample(wo, &hit, &mut sampler).unwrap();
            assert!(s.wi.dot(hit.normal) >= -1e-4);
            assert!((s.f - m.eval(wo, s.wi, &hit)).length() < 1e-5);
            assert!((s.pdf - m.pdf(wo, s.wi, &hit)).abs() < 1e-5);
            assert!(!s.specular);
        }
    }

    #[test]
    fn emissive_does_not_scatter_but_emits() {
        let mut sampler = Sampler::random(2);
        let m = Material::Emissive {
            emit: Color::new(4.0, 4.0, 4.0),
        };
        assert!(m
            .sample(Vec3::new(0.0, 1.0, 0.0), &flat_hit(true), &mut sampler)
            .is_none());
        assert_eq!(m.emitted(), Color::new(4.0, 4.0, 4.0));
        assert!(!m.is_specular());
    }

    #[test]
    fn mirror_metal_reflects_and_is_specular() {
        let mut sampler = Sampler::random(3);
        let m = Material::Metal {
            albedo: Color::ONE.into(),
            roughness: 0.0,
        };
        assert!(m.is_specular());
        let wo = Vec3::new(-1.0, 1.0, 0.0).normalize();
        let s = m.sample(wo, &flat_hit(true), &mut sampler).unwrap();
        assert!(s.specular);
        let expected = Vec3::new(1.0, 1.0, 0.0).normalize();
        assert!((s.wi - expected).length() < 1e-4);
    }

    #[test]
    fn rough_metal_is_non_specular_and_energy_conserving() {
        // With F0 = 1 (white), the single-scattering throughput weight is
        // G2/G1 <= 1, so the directional albedo must not exceed ~1.
        let mut sampler = Sampler::random(7);
        let m = Material::Metal {
            albedo: Color::ONE.into(),
            roughness: 0.3,
        };
        assert!(!m.is_specular());
        let hit = flat_hit(true);
        let wo = Vec3::new(0.3, 1.0, 0.0).normalize();
        let n = 40_000;
        let mut sum = 0.0;
        for _ in 0..n {
            // Below-surface microfacet reflections are legitimately rejected
            // (None) and contribute zero to the directional albedo.
            if let Some(s) = m.sample(wo, &hit, &mut sampler) {
                assert!(!s.specular);
                let cos = s.wi.dot(hit.normal).max(0.0);
                let weight = (s.f * (cos / s.pdf)).max_component();
                assert!(weight.is_finite() && weight <= 1.02, "weight {weight} > 1");
                sum += weight;
            }
        }
        let directional_albedo = sum / n as Float;
        assert!(
            directional_albedo > 0.3 && directional_albedo <= 1.02,
            "directional albedo out of range: {directional_albedo}"
        );
    }

    #[test]
    fn dielectric_is_specular_and_white() {
        let mut sampler = Sampler::random(4);
        let m = Material::Dielectric { ior: 1.5 };
        let s = m
            .sample(Vec3::new(0.0, 1.0, 0.0), &flat_hit(true), &mut sampler)
            .unwrap();
        assert!(s.specular);
        assert_eq!(s.f, Color::ONE);
    }
}
