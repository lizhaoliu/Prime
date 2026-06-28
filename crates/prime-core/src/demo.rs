//! Built-in scenes, so the renderer runs with zero external assets.

use crate::camera::CameraConfig;
use crate::geometry::{Primitive, Sphere, Triangle};
use crate::material::Material;
use crate::math::Vec3;
use crate::scene::{Background, Scene};
use crate::{Color, MaterialId};

/// Add a quad (as two triangles) spanning corners `a, b, c, d` (in order).
fn quad(prims: &mut Vec<Primitive>, a: Vec3, b: Vec3, c: Vec3, d: Vec3, m: MaterialId) {
    prims.push(Triangle::new(a, b, c, m).into());
    prims.push(Triangle::new(a, c, d, m).into());
}

/// The classic Cornell box: a great showcase of global illumination, soft
/// shadows, and color bleeding. Coordinates follow the canonical 0..555 layout.
pub fn cornell_box() -> Scene {
    let red = Material::Lambertian {
        albedo: Color::new(0.65, 0.05, 0.05),
    };
    let green = Material::Lambertian {
        albedo: Color::new(0.12, 0.45, 0.15),
    };
    let white = Material::Lambertian {
        albedo: Color::new(0.73, 0.73, 0.73),
    };
    let light = Material::Emissive {
        emit: Color::splat(15.0),
    };
    let glass = Material::Dielectric { ior: 1.5 };
    let metal = Material::Metal {
        albedo: Color::new(0.8, 0.85, 0.88),
        fuzz: 0.0,
    };

    let materials = vec![red, green, white, light, glass, metal];
    const RED: MaterialId = 0;
    const GREEN: MaterialId = 1;
    const WHITE: MaterialId = 2;
    const LIGHT: MaterialId = 3;
    const GLASS: MaterialId = 4;
    const METAL: MaterialId = 5;

    let mut prims = Vec::new();

    // Left wall (green) at x = 555, right wall (red) at x = 0.
    quad(
        &mut prims,
        Vec3::new(555.0, 0.0, 0.0),
        Vec3::new(555.0, 555.0, 0.0),
        Vec3::new(555.0, 555.0, 555.0),
        Vec3::new(555.0, 0.0, 555.0),
        GREEN,
    );
    quad(
        &mut prims,
        Vec3::new(0.0, 0.0, 0.0),
        Vec3::new(0.0, 0.0, 555.0),
        Vec3::new(0.0, 555.0, 555.0),
        Vec3::new(0.0, 555.0, 0.0),
        RED,
    );
    // Floor, ceiling, back wall (white).
    quad(
        &mut prims,
        Vec3::new(0.0, 0.0, 0.0),
        Vec3::new(555.0, 0.0, 0.0),
        Vec3::new(555.0, 0.0, 555.0),
        Vec3::new(0.0, 0.0, 555.0),
        WHITE,
    );
    quad(
        &mut prims,
        Vec3::new(0.0, 555.0, 0.0),
        Vec3::new(0.0, 555.0, 555.0),
        Vec3::new(555.0, 555.0, 555.0),
        Vec3::new(555.0, 555.0, 0.0),
        WHITE,
    );
    quad(
        &mut prims,
        Vec3::new(0.0, 0.0, 555.0),
        Vec3::new(555.0, 0.0, 555.0),
        Vec3::new(555.0, 555.0, 555.0),
        Vec3::new(0.0, 555.0, 555.0),
        WHITE,
    );
    // Ceiling light.
    quad(
        &mut prims,
        Vec3::new(213.0, 554.0, 227.0),
        Vec3::new(343.0, 554.0, 227.0),
        Vec3::new(343.0, 554.0, 332.0),
        Vec3::new(213.0, 554.0, 332.0),
        LIGHT,
    );

    // A glass and a metal sphere on the floor.
    prims.push(Primitive::Sphere(Sphere::new(
        Vec3::new(190.0, 90.0, 190.0),
        90.0,
        GLASS,
    )));
    prims.push(Primitive::Sphere(Sphere::new(
        Vec3::new(370.0, 90.0, 350.0),
        90.0,
        METAL,
    )));

    let camera = CameraConfig {
        look_from: Vec3::new(278.0, 278.0, -800.0),
        look_at: Vec3::new(278.0, 278.0, 0.0),
        vup: Vec3::new(0.0, 1.0, 0.0),
        vfov: 40.0,
        aperture: 0.0,
        focus_dist: None,
    };

    Scene::new(materials, prims, camera, Background::Solid(Color::ZERO))
}

/// A simple outdoor scene: a large ground sphere with diffuse, metal, and glass
/// spheres under a sky gradient. Fast to converge; good for smoke tests.
pub fn spheres() -> Scene {
    let ground = Material::Lambertian {
        albedo: Color::new(0.5, 0.5, 0.5),
    };
    let diffuse = Material::Lambertian {
        albedo: Color::new(0.7, 0.3, 0.3),
    };
    let glass = Material::Dielectric { ior: 1.5 };
    let metal = Material::Metal {
        albedo: Color::new(0.8, 0.6, 0.2),
        fuzz: 0.05,
    };

    let materials = vec![ground, diffuse, glass, metal];
    let prims = vec![
        Primitive::Sphere(Sphere::new(Vec3::new(0.0, -1000.0, 0.0), 1000.0, 0)),
        Primitive::Sphere(Sphere::new(Vec3::new(-2.2, 1.0, 0.0), 1.0, 1)),
        Primitive::Sphere(Sphere::new(Vec3::new(0.0, 1.0, 0.0), 1.0, 2)),
        Primitive::Sphere(Sphere::new(Vec3::new(2.2, 1.0, 0.0), 1.0, 3)),
    ];

    let camera = CameraConfig {
        look_from: Vec3::new(0.0, 1.6, 6.0),
        look_at: Vec3::new(0.0, 0.8, 0.0),
        vup: Vec3::new(0.0, 1.0, 0.0),
        vfov: 40.0,
        aperture: 0.04,
        focus_dist: Some(6.0),
    };

    Scene::new(materials, prims, camera, Background::default())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn demo_scenes_build() {
        assert!(cornell_box().primitive_count() > 0);
        assert!(spheres().primitive_count() > 0);
    }
}
