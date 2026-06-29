//! `prime-serve` — an interactive web viewer for the Prime path tracer.
//!
//! Renders a scene progressively on a background thread and streams the
//! accumulating image to a browser, which provides orbit/zoom camera control
//! and live render settings. All rendering lives in `prime-core`; this binary
//! is a thin HTTP front-end built on the blocking `tiny_http` server.

mod loader;
mod render;

use std::sync::mpsc::{self, Sender};
use std::sync::{Arc, Mutex};
use std::thread;

use anyhow::{anyhow, Context, Result};
use clap::Parser;
use serde::Deserialize;
use tiny_http::{Header, Method, Request, Response, Server};

use prime_core::color::Tonemap;
use prime_core::Float;

use render::{encode_png, render_loop, Cmd, Orbit, Published, Settings, Shared};

/// Interactive web viewer for the Prime path tracer.
#[derive(Parser, Debug)]
#[command(name = "prime-serve", version, about, long_about = None)]
struct Args {
    /// Scene: a built-in name (showcase, studio, rtweekend, sky, cornell, spheres),
    /// a .ron scene, or a .obj mesh.
    #[arg(default_value = "showcase")]
    scene: String,

    /// Address to bind.
    #[arg(long, default_value = "127.0.0.1")]
    addr: String,

    /// Port to listen on.
    #[arg(short, long, default_value_t = 8080)]
    port: u16,

    /// Render width in pixels.
    #[arg(short, long, default_value_t = 640)]
    width: usize,

    /// Render height in pixels.
    #[arg(long, default_value_t = 400)]
    height: usize,

    /// Max path bounce depth (lower = faster, dimmer indirect light).
    #[arg(short, long, default_value_t = 12)]
    depth: usize,

    /// Samples added per progressive pass.
    #[arg(long = "samples-per-pass", default_value_t = 2)]
    samples_per_pass: usize,

    /// Stop accumulating once this many samples-per-pixel are reached.
    #[arg(long = "target-spp", default_value_t = 1024)]
    target_spp: usize,

    /// Display gamma.
    #[arg(long, default_value_t = 2.2)]
    gamma: Float,

    /// Tonemap operator: clamp | reinhard.
    #[arg(long, default_value = "reinhard")]
    tonemap: String,

    /// Clamp per-sample radiance to suppress fireflies in the live preview
    /// (0 = disabled).
    #[arg(long, default_value_t = 8.0)]
    clamp: Float,
}

const INDEX: &str = include_str!("index.html");

fn main() -> Result<()> {
    let args = Args::parse();
    let aspect = args.width as Float / args.height as Float;

    let scene = loader::resolve(&args.scene, aspect)
        .with_context(|| format!("loading scene '{}'", args.scene))?;
    let orbit = Orbit::from_camera(&scene.camera);
    let settings = Settings {
        width: args.width,
        height: args.height,
        max_depth: args.depth,
        samples_per_pass: args.samples_per_pass,
        target_spp: args.target_spp,
        gamma: args.gamma,
        tonemap: parse_tonemap(&args.tonemap),
    };

    // A neutral placeholder so /frame.png is valid before the first pass lands.
    let placeholder = encode_png(
        &vec![32u8; args.width * args.height * 3],
        args.width,
        args.height,
    );
    let shared = Arc::new(Shared {
        published: Mutex::new(Published {
            png: placeholder,
            samples: 0,
            settings,
            orbit,
            scene: args.scene.clone(),
        }),
    });

    let (tx, rx) = mpsc::channel();
    {
        let shared = shared.clone();
        let scene_name = args.scene.clone();
        let clamp = args.clamp;
        thread::spawn(move || render_loop(rx, shared, scene, settings, orbit, scene_name, clamp));
    }

    let addr = format!("{}:{}", args.addr, args.port);
    let server = Server::http(&addr).map_err(|e| anyhow!("failed to bind {addr}: {e}"))?;
    println!(
        "Prime viewer running at http://{addr}/  (scene: {}, {}x{})",
        args.scene, args.width, args.height
    );

    for req in server.incoming_requests() {
        handle(req, &shared, &tx);
    }
    Ok(())
}

fn handle(mut req: Request, shared: &Arc<Shared>, tx: &Sender<Cmd>) {
    let method = req.method().clone();
    let raw = req.url().to_string();
    let path = raw.split('?').next().unwrap_or("/");

    let result = match (&method, path) {
        (&Method::Get, "/") => req.respond(
            Response::from_string(INDEX)
                .with_header(header("Content-Type", "text/html; charset=utf-8")),
        ),
        (&Method::Get, "/frame.png") => {
            let png = shared.published.lock().unwrap().png.clone();
            req.respond(
                Response::from_data(png)
                    .with_header(header("Content-Type", "image/png"))
                    .with_header(header("Cache-Control", "no-store")),
            )
        }
        (&Method::Get, "/status") => {
            let body = status_json(shared);
            req.respond(
                Response::from_string(body).with_header(header("Content-Type", "application/json")),
            )
        }
        (&Method::Post, "/camera") => {
            if let Ok(c) = serde_json::from_str::<CameraReq>(&read_body(&mut req)) {
                let _ = tx.send(Cmd::Camera {
                    dyaw: c.dyaw,
                    dpitch: c.dpitch,
                    zoom: c.zoom,
                });
            }
            req.respond(ok_json())
        }
        (&Method::Post, "/settings") => {
            if let Ok(s) = serde_json::from_str::<SettingsReq>(&read_body(&mut req)) {
                let _ = tx.send(Cmd::Settings(s.into_settings()));
            }
            req.respond(ok_json())
        }
        (&Method::Post, "/scene") => {
            if let Ok(s) = serde_json::from_str::<SceneReq>(&read_body(&mut req)) {
                let _ = tx.send(Cmd::Scene(s.name));
            }
            req.respond(ok_json())
        }
        _ => req.respond(Response::from_string("not found").with_status_code(404)),
    };
    if let Err(e) = result {
        eprintln!("response error: {e}");
    }
}

fn status_json(shared: &Arc<Shared>) -> String {
    let p = shared.published.lock().unwrap();
    serde_json::json!({
        "samples": p.samples,
        "target": p.settings.target_spp,
        "width": p.settings.width,
        "height": p.settings.height,
        "maxDepth": p.settings.max_depth,
        "samplesPerPass": p.settings.samples_per_pass,
        "gamma": p.settings.gamma,
        "tonemap": tonemap_str(p.settings.tonemap),
        "scene": p.scene,
        "yaw": p.orbit.yaw,
        "pitch": p.orbit.pitch,
        "distance": p.orbit.distance,
    })
    .to_string()
}

fn read_body(req: &mut Request) -> String {
    let mut s = String::new();
    let _ = req.as_reader().read_to_string(&mut s);
    s
}

fn header(key: &str, value: &str) -> Header {
    Header::from_bytes(key.as_bytes(), value.as_bytes()).expect("valid header")
}

fn ok_json() -> Response<std::io::Cursor<Vec<u8>>> {
    Response::from_string("{\"ok\":true}").with_header(header("Content-Type", "application/json"))
}

fn parse_tonemap(s: &str) -> Tonemap {
    match s.to_ascii_lowercase().as_str() {
        "reinhard" => Tonemap::Reinhard,
        _ => Tonemap::Clamp,
    }
}

fn tonemap_str(t: Tonemap) -> &'static str {
    match t {
        Tonemap::Reinhard => "reinhard",
        Tonemap::Clamp => "clamp",
    }
}

#[derive(Deserialize)]
struct CameraReq {
    #[serde(default)]
    dyaw: Float,
    #[serde(default)]
    dpitch: Float,
    #[serde(default = "one")]
    zoom: Float,
}

fn one() -> Float {
    1.0
}

#[derive(Deserialize)]
struct SettingsReq {
    width: usize,
    height: usize,
    max_depth: usize,
    samples_per_pass: usize,
    target_spp: usize,
    gamma: Float,
    tonemap: String,
}

impl SettingsReq {
    fn into_settings(self) -> Settings {
        Settings {
            width: self.width.clamp(16, 4000),
            height: self.height.clamp(16, 4000),
            max_depth: self.max_depth.clamp(1, 64),
            samples_per_pass: self.samples_per_pass.clamp(1, 64),
            target_spp: self.target_spp.clamp(1, 1_000_000),
            gamma: self.gamma.clamp(0.5, 4.0),
            tonemap: parse_tonemap(&self.tonemap),
        }
    }
}

#[derive(Deserialize)]
struct SceneReq {
    name: String,
}
