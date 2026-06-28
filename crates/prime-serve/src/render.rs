//! The background render thread and the state it owns.
//!
//! Concurrency model: a single thread owns all mutable render state (scene,
//! orbit camera, settings, accumulation buffer). HTTP handlers never touch it
//! directly — they send [`Cmd`]s over a channel and read the latest
//! [`Published`] frame under a mutex. This keeps the renderer free of locking
//! on its hot path and makes data races structurally impossible.

use std::sync::mpsc::{Receiver, RecvTimeoutError};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use image::ImageEncoder;
use prime_core::camera::CameraConfig;
use prime_core::color::Tonemap;
use prime_core::integrator::ProgressiveRenderer;
use prime_core::math::Vec3;
use prime_core::scene::Scene;
use prime_core::Float;

use crate::loader;

/// Commands from HTTP handlers to the render thread.
pub enum Cmd {
    /// Relative orbit: add to yaw/pitch, multiply distance by `zoom`.
    Camera {
        dyaw: Float,
        dpitch: Float,
        zoom: Float,
    },
    /// Replace render settings (resolution, quality, tonemap).
    Settings(Settings),
    /// Switch to a different scene by source string.
    Scene(String),
}

#[derive(Clone, Copy)]
pub struct Settings {
    pub width: usize,
    pub height: usize,
    pub max_depth: usize,
    pub samples_per_pass: usize,
    pub target_spp: usize,
    pub gamma: Float,
    pub tonemap: Tonemap,
}

impl Settings {
    fn aspect(&self) -> Float {
        self.width as Float / self.height as Float
    }
}

/// Orbit camera: a target point plus spherical coordinates.
#[derive(Clone, Copy)]
pub struct Orbit {
    pub target: Vec3,
    pub yaw: Float,
    pub pitch: Float,
    pub distance: Float,
    pub vfov: Float,
}

impl Orbit {
    pub fn from_camera(c: &CameraConfig) -> Orbit {
        let v = c.look_from - c.look_at;
        let distance = v.length().max(1e-3);
        let pitch = (v.y / distance).clamp(-1.0, 1.0).asin();
        let yaw = v.x.atan2(v.z);
        Orbit {
            target: c.look_at,
            yaw,
            pitch,
            distance,
            vfov: c.vfov,
        }
    }

    pub fn to_camera(self) -> CameraConfig {
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

/// Latest render output and parameters, read by HTTP handlers.
pub struct Published {
    pub png: Vec<u8>,
    pub samples: usize,
    pub settings: Settings,
    pub orbit: Orbit,
    pub scene: String,
}

pub struct Shared {
    pub published: Mutex<Published>,
}

/// Encode interleaved RGB8 bytes as an in-memory PNG.
pub fn encode_png(rgb: &[u8], width: usize, height: usize) -> Vec<u8> {
    let mut out = Vec::new();
    image::codecs::png::PngEncoder::new(&mut out)
        .write_image(
            rgb,
            width as u32,
            height as u32,
            image::ExtendedColorType::Rgb8,
        )
        .expect("PNG encode should not fail for a valid RGB buffer");
    out
}

/// Run the render loop until the command channel disconnects. `firefly_clamp`
/// is a server-wide setting (from the CLI) that suppresses speckles in the live
/// preview; `<= 0` disables it.
pub fn render_loop(
    rx: Receiver<Cmd>,
    shared: Arc<Shared>,
    mut scene: Scene,
    mut settings: Settings,
    mut orbit: Orbit,
    mut scene_name: String,
    firefly_clamp: Float,
) {
    let mut renderer = new_renderer(&orbit, &settings, firefly_clamp);

    loop {
        // If converged, block (with a timeout) so we don't spin; otherwise just
        // drain whatever is queued and keep rendering.
        let mut dirty = false;
        if renderer.samples() >= settings.target_spp.max(1) {
            match rx.recv_timeout(Duration::from_millis(100)) {
                Ok(cmd) => {
                    apply(cmd, &mut settings, &mut orbit, &mut scene, &mut scene_name);
                    dirty = true;
                }
                Err(RecvTimeoutError::Timeout) => {}
                Err(RecvTimeoutError::Disconnected) => return,
            }
        }
        while let Ok(cmd) = rx.try_recv() {
            apply(cmd, &mut settings, &mut orbit, &mut scene, &mut scene_name);
            dirty = true;
        }
        if dirty {
            renderer = new_renderer(&orbit, &settings, firefly_clamp);
        }

        let target = settings.target_spp.max(1);
        if renderer.samples() < target {
            let remaining = target - renderer.samples();
            let count = settings.samples_per_pass.clamp(1, remaining);
            renderer.render_pass(&scene, count);
            publish(&shared, &renderer, &settings, &orbit, &scene_name);
        }
    }
}

fn new_renderer(orbit: &Orbit, settings: &Settings, firefly_clamp: Float) -> ProgressiveRenderer {
    ProgressiveRenderer::new(
        &orbit.to_camera(),
        settings.width.max(1),
        settings.height.max(1),
        settings.max_depth.max(1),
        0,
        firefly_clamp,
    )
}

fn apply(
    cmd: Cmd,
    settings: &mut Settings,
    orbit: &mut Orbit,
    scene: &mut Scene,
    scene_name: &mut String,
) {
    match cmd {
        Cmd::Camera { dyaw, dpitch, zoom } => {
            orbit.yaw += dyaw;
            orbit.pitch = (orbit.pitch + dpitch).clamp(-1.5, 1.5);
            orbit.distance = (orbit.distance * zoom).max(1e-3);
        }
        Cmd::Settings(s) => {
            *settings = s;
        }
        Cmd::Scene(name) => match loader::resolve(&name, settings.aspect()) {
            Ok(new_scene) => {
                *orbit = Orbit::from_camera(&new_scene.camera);
                *scene = new_scene;
                *scene_name = name;
            }
            Err(e) => eprintln!("scene '{name}' failed to load: {e:#}"),
        },
    }
}

fn publish(
    shared: &Shared,
    renderer: &ProgressiveRenderer,
    settings: &Settings,
    orbit: &Orbit,
    scene_name: &str,
) {
    let bytes = renderer.to_srgb_bytes(settings.tonemap, settings.gamma);
    let png = encode_png(&bytes, renderer.width(), renderer.height());
    let mut p = shared.published.lock().unwrap();
    p.png = png;
    p.samples = renderer.samples();
    p.settings = *settings;
    p.orbit = *orbit;
    p.scene = scene_name.to_string();
}
