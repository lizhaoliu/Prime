//! Performance baselines for the hot paths: BVH build, ray traversal, and
//! end-to-end rendering. Run with `cargo bench -p prime-core`.

use criterion::{black_box, criterion_group, criterion_main, BatchSize, Criterion, Throughput};
use prime_core::aabb::Aabb;
use prime_core::bvh::Bvh;
use prime_core::camera::{Camera, CameraConfig};
use prime_core::geometry::{Primitive, Sphere};
use prime_core::integrator::{render, RenderSettings};
use prime_core::math::Vec3;
use prime_core::obj::{self, Transform};
use prime_core::ray::Ray;
use prime_core::sampler::Sampler;
use prime_core::{demo, Float};

/// The committed bunny + buddha + plane mesh (~170k triangles).
const OBJ: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../assets/scene.obj");

fn sphere_grid(half: i32) -> Vec<Primitive> {
    let mut v = Vec::new();
    for x in -half..half {
        for y in -half..half {
            for z in -half..half {
                let c = Vec3::new(x as Float * 1.5, y as Float * 1.5, z as Float * 1.5);
                v.push(Sphere::new(c, 0.5, 0).into());
            }
        }
    }
    v
}

fn obj_prims() -> Option<Vec<Primitive>> {
    obj::load(OBJ, 0, Transform::default())
        .ok()
        .map(|tris| tris.into_iter().map(Primitive::from).collect())
}

fn bvh_build(c: &mut Criterion) {
    let mut g = c.benchmark_group("bvh_build");

    let spheres = sphere_grid(13); // 26^3 ≈ 17,576 spheres
    g.bench_function("spheres_17k", |b| {
        b.iter_batched(
            || spheres.clone(),
            |p| black_box(Bvh::build(p)),
            BatchSize::LargeInput,
        )
    });

    if let Some(prims) = obj_prims() {
        g.bench_function("bunny_buddha_170k_tris", |b| {
            b.iter_batched(
                || prims.clone(),
                |p| black_box(Bvh::build(p)),
                BatchSize::LargeInput,
            )
        });
    }
    g.finish();
}

/// A grid of primary rays aimed at a bounding box (for traversal throughput).
fn camera_rays(bounds: Aabb, res: usize) -> Vec<Ray> {
    let center = bounds.centroid();
    let radius = ((bounds.max - bounds.min).length() * 0.5).max(1e-3);
    let cfg = CameraConfig {
        look_from: center + Vec3::new(radius * 0.6, radius * 0.4, radius * 2.0),
        look_at: center,
        vup: Vec3::new(0.0, 1.0, 0.0),
        vfov: 40.0,
        aperture: 0.0,
        focus_dist: None,
    };
    let cam = Camera::new(&cfg, 1.0);
    let mut s = Sampler::random(1);
    let mut rays = Vec::with_capacity(res * res);
    for y in 0..res {
        for x in 0..res {
            let u = (x as Float + 0.5) / res as Float;
            let v = (y as Float + 0.5) / res as Float;
            rays.push(cam.get_ray(u, v, &mut s));
        }
    }
    rays
}

fn traversal(c: &mut Criterion) {
    let Some(prims) = obj_prims() else {
        return;
    };
    let bounds = prims.iter().fold(Aabb::EMPTY, |b, p| b.union(p.aabb()));
    let bvh = Bvh::build(prims);
    let rays = camera_rays(bounds, 400); // 160k rays

    let mut g = c.benchmark_group("traversal_bunny_buddha");
    g.throughput(Throughput::Elements(rays.len() as u64));
    g.bench_function("closest_hit", |b| {
        b.iter(|| {
            let mut hits = 0u32;
            for r in &rays {
                if bvh.hit(r, 1e-3, Float::INFINITY).is_some() {
                    hits += 1;
                }
            }
            black_box(hits)
        })
    });
    g.bench_function("occluded", |b| {
        b.iter(|| {
            let mut blocked = 0u32;
            for r in &rays {
                if bvh.occluded(r, 1e-3, Float::INFINITY) {
                    blocked += 1;
                }
            }
            black_box(blocked)
        })
    });
    g.finish();
}

fn render_scenes(c: &mut Criterion) {
    let settings = RenderSettings {
        width: 256,
        height: 256,
        samples_per_pixel: 16,
        max_depth: 8,
        ..Default::default()
    };
    let mut g = c.benchmark_group("render_256x256x16spp");
    for (name, scene) in [
        ("cornell", demo::cornell_box()),
        ("rtweekend", demo::rtweekend()),
    ] {
        g.bench_function(name, |b| {
            b.iter(|| black_box(render(&scene, &settings, || {})))
        });
    }
    g.finish();
}

criterion_group! {
    name = benches;
    config = Criterion::default().sample_size(20);
    targets = bvh_build, traversal, render_scenes
}
criterion_main!(benches);
