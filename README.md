# Prime

A headless, physically based **path tracer** written in Rust.

Prime renders scenes by Monte-Carlo path tracing: it shoots rays from a camera,
bounces them around a scene of geometry and materials, and accumulates the light
that reaches the eye. Output is a PNG. There is no GUI and no GPU dependency —
it builds and runs anywhere `cargo` does.

```
prime cornell -o cornell.png --width 800 --height 800 --samples 256
```

| Scene | Command |
|-------|---------|
| Cornell box (global illumination, glass + metal) | `prime cornell` |
| Sphere field under a sky (defocus blur) | `prime spheres` |
| Custom scene from a file | `prime myscene.ron` |
| A bare mesh, auto-framed | `prime model.obj` |
| **Interactive viewer in the browser** | `prime-serve cornell` → open http://127.0.0.1:8080 |

---

## Why this exists / what changed

Prime began as a ~5,700-line Java 1.7 renderer with a Swing + JOGL (OpenGL)
scene editor. That code is preserved under [`legacy-java/`](legacy-java/) for
reference. It was rebuilt from scratch in Rust with a focus on **a clean,
headless, testable architecture**. The most important changes:

| Concern | Legacy (Java) | Now (Rust) |
|--------|----------------|------------|
| Entry point | 1,292-line `MainGui` god class (Swing/JOGL, IO, render loop, dialogs all in one) | A pure `prime-core` library + a thin `prime` CLI |
| Math types | Mutable `Vec3f`/`Color3f` with public fields and shared mutable `ZERO`/`UNIT_*` singletons | Immutable `Copy` `Vec3`; no aliasing hazards, **zero heap allocation** in hot loops |
| Materials | Abstract base + subclasses, transmission never implemented, dead BRDF branches | Sealed `enum Material` (Lambertian / Metal / **Dielectric** / Emissive), exhaustively checked, no virtual dispatch |
| Acceleration | kd-tree whose traversal mutated the ray's length to prune | Binned-SAH **BVH** in a flat node array, iterative stack traversal, cross-checked against brute force |
| Parallelism | A fresh `ExecutorService` leaked on every render | Rayon over the global pool; deterministic per-pixel RNG (reproducible images) |
| Scene I/O | Java `Serializable` + `ObjectOutputStream` (fragile, unsafe) | Human-readable **RON** scene files via Serde |
| Asset loading | OBJ loader coupled to the GUI (`new ContentLoader(mainGui)`) | Pure `obj::load(path, …) -> Result<Vec<Triangle>, ObjError>` |
| Dependencies | log4j 1.2.17, commons-collections 3.2.1 (CVEs), JOGL 2.1.2 (dead `javax.media.opengl`) | A small set of current, audited crates |
| Tests | None | Unit tests across math, geometry, BVH, materials, camera, color, OBJ, scene |

---

## Architecture

A Cargo workspace with three crates:

```
crates/
  prime-core/   # the renderer as a pure library (no windowing, no image codec)
  prime-cli/    # the `prime` binary: argument parsing, PNG output, progress bar
  prime-serve/  # the `prime-serve` binary: an interactive web viewer
```

Both front-ends are thin shells over `prime-core`. The library knows nothing
about PNGs, HTTP, or argument parsing — exactly the decoupling the legacy
GUI-coupled design lacked.

### `prime-core` modules

```
math/        Vec3 (immutable Copy), orthonormal basis, Monte-Carlo sampling
ray          origin + direction
aabb         axis-aligned box + slab test
geometry/    Sphere, Triangle (Möller–Trumbore), sealed `Primitive` enum
bvh          binned-SAH bounding volume hierarchy, iterative traversal
hit          intersection record (point, oriented normal, uv, material id)
material     sealed BSDF enum: Lambertian / Metal / Dielectric / Emissive
camera       thin-lens pinhole camera (look-at, fov, optional defocus)
scene        material table + BVH + camera config + background
integrator   the parallel path tracer (Russian roulette, RR-unbiased)
framebuffer  linear HDR pixel buffer -> sRGB bytes
color        tonemapping (clamp / Reinhard) + gamma
obj          Wavefront OBJ loader (no UI dependency)
desc         serializable `SceneDesc` (RON) -> `Scene`
demo         built-in Cornell box and sphere scenes
```

### Pipeline

`Scene` (materials + `Bvh<Primitive>` + `CameraConfig` + `Background`) →
`integrator::render` (Rayon-parallel over scanlines, iterative path tracing) →
`Framebuffer` (linear HDR) → `color::to_srgb8` → PNG.

---

## Build & run

Requires a recent stable Rust toolchain (1.96+).

```bash
cargo build --release          # build everything
cargo test                     # run the unit tests
cargo run --release -- cornell -o out/cornell.png --samples 256
```

### CLI options

```
prime [SCENE] [OPTIONS]

SCENE                     built-in name (cornell, spheres), a .ron scene,
                          or a .obj mesh                       [default: cornell]
-o, --output <FILE>       output PNG                           [default: out.png]
-w, --width  <N>          image width                          [default: 800]
    --height <N>          image height                         [default: 450]
-s, --samples <N>         samples per pixel                    [default: 64]
-d, --depth <N>           max bounce depth                     [default: 32]
    --seed <N>            RNG seed (reproducible)              [default: 0]
-j, --threads <N>         worker threads                       [default: all cores]
    --tonemap <T>         clamp | reinhard                     [default: clamp]
    --gamma <F>           display gamma                        [default: 2.2]
```

---

## Interactive web viewer

`prime-serve` renders a scene **progressively** on a background thread and
streams the accumulating image to a browser. Drag to orbit, scroll to zoom, and
tweak quality/tonemap/resolution live — every change restarts accumulation and
the image refines while idle.

```bash
cargo run --release -p prime-serve -- cornell --width 720 --height 540
# then open http://127.0.0.1:8080
```

```
prime-serve [SCENE] [OPTIONS]

SCENE                       built-in name, .ron, or .obj          [default: cornell]
    --addr <ADDR>           bind address                          [default: 127.0.0.1]
-p, --port <N>              port                                  [default: 8080]
-w, --width / --height      render resolution                    [default: 640x400]
-d, --depth <N>             max bounce depth                      [default: 12]
    --samples-per-pass <N>  samples added per progressive pass    [default: 2]
    --target-spp <N>        stop accumulating at this many spp    [default: 1024]
    --tonemap <T>           clamp | reinhard                      [default: reinhard]
    --gamma <F>             display gamma                         [default: 2.2]
```

Design: a single render thread owns all mutable state (scene, orbit camera,
accumulation buffer) and is driven by commands from the HTTP handlers over a
channel; handlers only read the latest published frame under a mutex. The
renderer never locks on its hot path, and data races are structurally
impossible. Endpoints: `GET /` (page), `GET /frame.png`, `GET /status`,
`POST /camera`, `POST /settings`, `POST /scene`.

---

## Scene format

Scenes are plain data in [RON](https://github.com/ron-rs/ron). Objects reference
materials by index. Meshes are loaded from OBJ paths relative to the scene file.
See [`assets/demo.ron`](assets/demo.ron) for a complete example:

```ron
(
    camera: ( look_from: (x: 0.0, y: 1.4, z: 6.5), look_at: (x: 0.0, y: 0.7, z: 0.0),
              vup: (x: 0.0, y: 1.0, z: 0.0), vfov: 45.0, aperture: 0.05, focus_dist: Some(6.5) ),
    background: Gradient( bottom: (x: 1.0, y: 1.0, z: 1.0), top: (x: 0.5, y: 0.7, z: 1.0) ),
    materials: [
        Lambertian(albedo: (x: 0.5, y: 0.5, z: 0.5)),
        Metal(albedo: (x: 0.85, y: 0.85, z: 0.88), fuzz: 0.0),
        Dielectric(ior: 1.5),
        Emissive(emit: (x: 8.0, y: 6.5, z: 5.0)),
    ],
    objects: [
        Sphere(center: (x: 0.0, y: 1.0, z: 0.0), radius: 1.0, material: 1),
        Mesh(path: "scene.obj", material: 0, transform: Some((scale: 1.0, translate: (x: 0.0, y: 0.0, z: 0.0)))),
    ],
)
```

---

## Performance

On a 28-core machine (release build):

* Cornell box, 800×800, 256 spp (glass + metal, deep bounces): **~3.3 s**
* Stanford Bunny + Happy Buddha (`assets/scene.obj`, 169,794 triangles),
  800×600, 64 spp: **~0.4 s**

Renders are deterministic: the same `--seed`, dimensions, and sample count
produce a byte-identical image regardless of thread count.

---

## License

MIT.
