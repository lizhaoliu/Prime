//! Monte-Carlo sampling helpers, generic over any [`rand::Rng`].
//!
//! These replace the legacy `MathUtils.random*` grab-bag, which mixed a global
//! Apache-Commons RNG with hand-rolled trigonometry.

use super::{onb::Onb, Vec3};
use crate::Float;
use rand::Rng;
use std::f32::consts::PI;

/// Uniformly sample a point inside the unit disk (z = 0). Used for camera
/// defocus blur.
pub fn random_in_unit_disk<R: Rng + ?Sized>(rng: &mut R) -> Vec3 {
    loop {
        let p = Vec3::new(rng.gen_range(-1.0..1.0), rng.gen_range(-1.0..1.0), 0.0);
        if p.length_squared() < 1.0 {
            return p;
        }
    }
}

/// A uniformly distributed unit vector on the sphere.
pub fn random_unit_vector<R: Rng + ?Sized>(rng: &mut R) -> Vec3 {
    let a: Float = rng.gen_range(0.0..(2.0 * PI));
    let z: Float = rng.gen_range(-1.0..1.0);
    let r = (1.0 - z * z).max(0.0).sqrt();
    Vec3::new(r * a.cos(), r * a.sin(), z)
}

/// Cosine-weighted hemisphere sample, expressed in the local frame (z = up).
/// The pdf is `cos(theta) / pi`, which cancels the Lambertian cosine term.
pub fn random_cosine_direction<R: Rng + ?Sized>(rng: &mut R) -> Vec3 {
    let r1: Float = rng.gen();
    let r2: Float = rng.gen();
    let phi = 2.0 * PI * r1;
    let r2_sqrt = r2.sqrt();
    let x = phi.cos() * r2_sqrt;
    let y = phi.sin() * r2_sqrt;
    let z = (1.0 - r2).sqrt();
    Vec3::new(x, y, z)
}

/// Cosine-weighted hemisphere sample oriented around world-space normal `n`.
pub fn cosine_weighted_hemisphere<R: Rng + ?Sized>(rng: &mut R, n: Vec3) -> Vec3 {
    Onb::from_w(n).local(random_cosine_direction(rng))
}

#[cfg(test)]
mod tests {
    use super::*;
    use rand::rngs::SmallRng;
    use rand::SeedableRng;

    #[test]
    fn cosine_samples_stay_in_hemisphere() {
        let mut rng = SmallRng::seed_from_u64(42);
        let n = Vec3::new(0.0, 1.0, 0.0);
        for _ in 0..10_000 {
            let d = cosine_weighted_hemisphere(&mut rng, n);
            assert!(d.dot(n) >= -1e-4, "sample fell below the hemisphere");
        }
    }

    #[test]
    fn unit_vectors_are_unit_length() {
        let mut rng = SmallRng::seed_from_u64(7);
        for _ in 0..1000 {
            assert!((random_unit_vector(&mut rng).length() - 1.0).abs() < 1e-4);
        }
    }
}
