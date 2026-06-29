// Phase A GPU kernel: one primary ray per pixel against a small set of uploaded
// spheres, shaded with a fixed directional light + sky ambient. No path tracing
// yet — this exists to prove the end-to-end GPU pipeline (upload -> launch ->
// shade -> read back -> PNG). Compiled at runtime with NVRTC.
//
// Buffer layouts (all f32, host-packed):
//   spheres: 8 floats each -> cx, cy, cz, radius, albedo_r, albedo_g, albedo_b, _pad
//   cam:     12 floats     -> origin(3), lower_left(3), horizontal(3), vertical(3)
//   out:     W*H*3 floats  -> linear RGB, row-major, row 0 = top

struct V3 {
    float x, y, z;
};
__device__ inline V3 v3(float x, float y, float z) {
    V3 r;
    r.x = x;
    r.y = y;
    r.z = z;
    return r;
}
__device__ inline V3 add(V3 a, V3 b) { return v3(a.x + b.x, a.y + b.y, a.z + b.z); }
__device__ inline V3 sub(V3 a, V3 b) { return v3(a.x - b.x, a.y - b.y, a.z - b.z); }
__device__ inline V3 scale(V3 a, float s) { return v3(a.x * s, a.y * s, a.z * s); }
__device__ inline float dot(V3 a, V3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
__device__ inline V3 normalize(V3 a) {
    float l = sqrtf(dot(a, a));
    return l > 0.0f ? scale(a, 1.0f / l) : a;
}

extern "C" __global__ void render(float* out, int W, int H, const float* sph, int nsph,
                                  const float* cam) {
    int px = blockIdx.x * blockDim.x + threadIdx.x;
    int py = blockIdx.y * blockDim.y + threadIdx.y;
    if (px >= W || py >= H) return;

    V3 origin = v3(cam[0], cam[1], cam[2]);
    V3 lower_left = v3(cam[3], cam[4], cam[5]);
    V3 horizontal = v3(cam[6], cam[7], cam[8]);
    V3 vertical = v3(cam[9], cam[10], cam[11]);

    float s = (px + 0.5f) / (float)W;
    float t = (H - 1 - py + 0.5f) / (float)H; // row 0 = top
    V3 dir = normalize(sub(add(add(lower_left, scale(horizontal, s)), scale(vertical, t)), origin));

    float best = 1e30f;
    int hit = -1;
    for (int i = 0; i < nsph; i++) {
        V3 c = v3(sph[i * 8 + 0], sph[i * 8 + 1], sph[i * 8 + 2]);
        float r = sph[i * 8 + 3];
        V3 oc = sub(origin, c);
        float b = dot(oc, dir);
        float cc = dot(oc, oc) - r * r;
        float disc = b * b - cc;
        if (disc > 0.0f) {
            float sq = sqrtf(disc);
            float tt = -b - sq;
            if (tt < 1e-3f) tt = -b + sq;
            if (tt > 1e-3f && tt < best) {
                best = tt;
                hit = i;
            }
        }
    }

    V3 col;
    if (hit >= 0) {
        V3 p = add(origin, scale(dir, best));
        V3 c = v3(sph[hit * 8 + 0], sph[hit * 8 + 1], sph[hit * 8 + 2]);
        float r = sph[hit * 8 + 3];
        V3 n = scale(sub(p, c), 1.0f / r);
        V3 albedo = v3(sph[hit * 8 + 4], sph[hit * 8 + 5], sph[hit * 8 + 6]);
        V3 light = normalize(v3(0.5f, 1.0f, 0.3f));
        float diffuse = fmaxf(0.0f, dot(n, light));
        col = scale(albedo, 0.2f + 0.8f * diffuse);
    } else {
        float tt = 0.5f * (dir.y + 1.0f);
        col = add(scale(v3(1.0f, 1.0f, 1.0f), 1.0f - tt), scale(v3(0.5f, 0.7f, 1.0f), tt));
    }

    int idx = (py * W + px) * 3;
    out[idx + 0] = col.x;
    out[idx + 1] = col.y;
    out[idx + 2] = col.z;
}
