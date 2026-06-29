//! `prime-gpu-serve` — an interactive GPU viewer in the browser.
//!
//! A background thread owns the GPU renderer and an orbit camera, accumulating
//! samples progressively (the image refines while idle). HTTP handlers only send
//! camera commands over a channel and read the latest published PNG under a
//! mutex — the renderer never locks on its hot path. Drag to orbit, scroll to
//! zoom; every move restarts accumulation.

use std::sync::mpsc::{self, Receiver, RecvTimeoutError};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

use anyhow::{anyhow, Result};
use clap::Parser;
use image::ImageEncoder;
use tiny_http::{Header, Method, Response, Server};

use prime_core::camera::CameraConfig;
use prime_core::color::Tonemap;
use prime_core::math::Vec3;
use prime_core::scene::Scene;
use prime_core::Float;
use prime_cuda::{load_scene, CamBasis, GpuScene};

const PAGE: &str = include_str!("viewer.html");

#[derive(Parser, Debug)]
#[command(
    name = "prime-gpu-serve",
    version,
    about = "Interactive GPU path-tracing viewer"
)]
struct Args {
    /// Scene: a built-in name, or a `.ron`/`.gltf`/`.obj` file.
    #[arg(default_value = "cornell")]
    scene: String,
    /// Bind address.
    #[arg(long, default_value = "127.0.0.1")]
    addr: String,
    /// Port.
    #[arg(short, long, default_value_t = 8080)]
    port: u16,
    #[arg(short, long, default_value_t = 720)]
    width: usize,
    #[arg(long, default_value_t = 540)]
    height: usize,
    /// Maximum path depth.
    #[arg(short, long, default_value_t = 8)]
    depth: usize,
    /// Samples accumulated per progressive pass.
    #[arg(long, default_value_t = 4)]
    samples_per_pass: usize,
    /// Stop accumulating at this many samples per pixel.
    #[arg(long, default_value_t = 2048)]
    target_spp: usize,
}

/// Relative orbit command from the browser.
struct Cmd {
    dyaw: Float,
    dpitch: Float,
    zoom: Float,
}

/// Orbit camera: a target plus spherical coordinates.
#[derive(Clone, Copy)]
struct Orbit {
    target: Vec3,
    yaw: Float,
    pitch: Float,
    distance: Float,
    vfov: Float,
}

impl Orbit {
    fn from_camera(c: &CameraConfig) -> Orbit {
        let v = c.look_from - c.look_at;
        let distance = v.length().max(1e-3);
        Orbit {
            target: c.look_at,
            yaw: v.x.atan2(v.z),
            pitch: (v.y / distance).clamp(-1.0, 1.0).asin(),
            distance,
            vfov: c.vfov,
        }
    }
    fn to_camera(self) -> CameraConfig {
        let (sy, cy) = self.yaw.sin_cos();
        let (sp, cp) = self.pitch.sin_cos();
        let dir = Vec3::new(cp * sy, sp, cp * cy);
        CameraConfig {
            look_from: self.target + dir * self.distance,
            look_at: self.target,
            vup: Vec3::new(0.0, 1.0, 0.0),
            vfov: self.vfov,
            aperture: 0.0,
            focus_dist: None,
        }
    }
}

struct Published {
    png: Vec<u8>,
    samples: usize,
    target: usize,
}

fn encode_png(rgb: &[u8], w: usize, h: usize) -> Vec<u8> {
    let mut out = Vec::new();
    image::codecs::png::PngEncoder::new(&mut out)
        .write_image(rgb, w as u32, h as u32, image::ExtendedColorType::Rgb8)
        .expect("PNG encode");
    out
}

fn main() -> Result<()> {
    let args = Args::parse();
    let (w, h) = (args.width, args.height);
    let scene = load_scene(&args.scene)?;
    let orbit = Orbit::from_camera(&scene.camera);

    let shared = Arc::new(Mutex::new(Published {
        png: encode_png(&vec![32u8; w * h * 3], w, h),
        samples: 0,
        target: args.target_spp,
    }));
    let (tx, rx) = mpsc::channel::<Cmd>();

    {
        let shared = shared.clone();
        let (depth, seed) = (args.depth, 0u32);
        let (pass, target) = (args.samples_per_pass.max(1), args.target_spp.max(1));
        thread::spawn(move || {
            render_loop(scene, w, h, depth, seed, pass, target, orbit, shared, rx);
        });
    }

    let addr = format!("{}:{}", args.addr, args.port);
    let server = Server::http(&addr).map_err(|e| anyhow!("failed to bind {addr}: {e}"))?;
    eprintln!(
        "prime-gpu-serve: http://{addr}  (scene '{}', {w}x{h})",
        args.scene
    );

    for mut req in server.incoming_requests() {
        let path = req.url().split('?').next().unwrap_or("/").to_string();
        match (req.method(), path.as_str()) {
            (Method::Get, "/") => {
                let h = Header::from_bytes(&b"Content-Type"[..], &b"text/html"[..]).unwrap();
                let _ = req.respond(Response::from_string(PAGE).with_header(h));
            }
            (Method::Get, "/frame.png") => {
                let png = shared.lock().unwrap().png.clone();
                let h = Header::from_bytes(&b"Content-Type"[..], &b"image/png"[..]).unwrap();
                let _ = req.respond(Response::from_data(png).with_header(h));
            }
            (Method::Get, "/status") => {
                let p = shared.lock().unwrap();
                let body = format!("{{\"samples\":{},\"target\":{}}}", p.samples, p.target);
                let h = Header::from_bytes(&b"Content-Type"[..], &b"application/json"[..]).unwrap();
                let _ = req.respond(Response::from_string(body).with_header(h));
            }
            (Method::Post, "/camera") => {
                let mut body = String::new();
                let _ = req.as_reader().read_to_string(&mut body);
                if let Ok(v) = serde_json::from_str::<serde_json::Value>(&body) {
                    let f =
                        |k: &str, d: f64| v.get(k).and_then(|x| x.as_f64()).unwrap_or(d) as Float;
                    let _ = tx.send(Cmd {
                        dyaw: f("dyaw", 0.0),
                        dpitch: f("dpitch", 0.0),
                        zoom: f("zoom", 1.0),
                    });
                }
                let _ = req.respond(Response::from_string("ok"));
            }
            _ => {
                let _ = req.respond(Response::from_string("not found").with_status_code(404));
            }
        }
    }
    Ok(())
}

#[allow(clippy::too_many_arguments)]
fn render_loop(
    scene: Scene,
    w: usize,
    h: usize,
    depth: usize,
    seed: u32,
    pass: usize,
    target: usize,
    mut orbit: Orbit,
    shared: Arc<Mutex<Published>>,
    rx: Receiver<Cmd>,
) {
    let gpu = match GpuScene::new(&scene, w, h, depth, seed) {
        Ok(g) => g,
        Err(e) => {
            eprintln!("GPU init failed: {e:#}");
            return;
        }
    };
    eprintln!("GPU: {}", gpu.device_name());
    let aspect = w as Float / h as Float;
    let mut accum = gpu.alloc_accum().expect("alloc accum");
    let mut total = 0usize;
    let mut cam = CamBasis::new(&orbit.to_camera(), aspect);

    loop {
        let mut dirty = false;
        if total >= target {
            // Converged: block for the next camera command instead of spinning.
            match rx.recv_timeout(Duration::from_millis(100)) {
                Ok(c) => {
                    apply(c, &mut orbit);
                    dirty = true;
                }
                Err(RecvTimeoutError::Timeout) => {}
                Err(RecvTimeoutError::Disconnected) => return,
            }
        }
        while let Ok(c) = rx.try_recv() {
            apply(c, &mut orbit);
            dirty = true;
        }
        if dirty {
            accum = gpu.alloc_accum().expect("alloc accum");
            total = 0;
            cam = CamBasis::new(&orbit.to_camera(), aspect);
        }

        if total < target {
            let n = pass.min(target - total);
            if gpu
                .render_pass(&mut accum, &cam, n as i32, total as i32)
                .is_err()
            {
                return;
            }
            total += n;
            let bytes = gpu
                .resolve_srgb(&accum, total as Float, Tonemap::Reinhard, 2.2)
                .expect("resolve");
            let png = encode_png(&bytes, w, h);
            let mut p = shared.lock().unwrap();
            p.png = png;
            p.samples = total;
        }
    }
}

fn apply(c: Cmd, orbit: &mut Orbit) {
    orbit.yaw += c.dyaw;
    orbit.pitch = (orbit.pitch + c.dpitch).clamp(-1.5, 1.5);
    orbit.distance = (orbit.distance * c.zoom).max(1e-3);
}
