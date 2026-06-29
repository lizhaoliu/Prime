// Phase C (increment 1) GPU path tracer: Lambertian + emissive, unidirectional
// path tracing with Russian roulette, many samples per pixel accumulated on the
// device. It reuses the Phase-B BVH traversal, so geometry is already validated;
// here we add shading. Output is linear HDR (host tonemaps).
//
// Sampling is plain white noise (per-pixel/per-sample PCG), not the CPU's QMC —
// but both are unbiased estimators of the same rendering equation, so the GPU
// image converges to the CPU reference (validated by RMSE on the host).
//
// Buffers: see prime_core::bvh::FlatBvh (nbounds/nmeta/pkind/pdata/pmat) plus a
// material table `mats` (8 f32/material: kind, albedo.xyz, emit.xyz, param) and
// the camera frame `cam` (12 f32). Material kinds: 0 = Lambertian, 1 = Metal,
// 2 = Emissive, 3 = Dielectric. Increment 1 shades 0 and 2; 1 and 3 are treated
// as diffuse for now (handled exactly in the next increment).

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
__device__ inline V3 mulv(V3 a, V3 b) { return v3(a.x * b.x, a.y * b.y, a.z * b.z); }
__device__ inline float dot(V3 a, V3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
__device__ inline V3 cross(V3 a, V3 b) {
    return v3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
}
__device__ inline V3 normalize(V3 a) {
    float l = sqrtf(dot(a, a));
    return l > 0.0f ? scale(a, 1.0f / l) : a;
}

// --- PCG random number generator -------------------------------------------
__device__ inline unsigned int pcg(unsigned int& state) {
    state = state * 747796405u + 2891336453u;
    unsigned int word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}
__device__ inline float randf(unsigned int& state) {
    return (pcg(state) >> 8) * (1.0f / 16777216.0f); // [0, 1)
}

// Cosine-weighted hemisphere direction around `n`.
__device__ inline V3 cosine_dir(V3 n, float r1, float r2) {
    float phi = 6.2831853f * r1;
    float r = sqrtf(r2);
    float x = r * cosf(phi), y = r * sinf(phi), z = sqrtf(fmaxf(0.0f, 1.0f - r2));
    V3 a = fabsf(n.x) > 0.9f ? v3(0.0f, 1.0f, 0.0f) : v3(1.0f, 0.0f, 0.0f);
    V3 t = normalize(cross(a, n));
    V3 b = cross(n, t);
    return normalize(add(add(scale(t, x), scale(b, y)), scale(n, z)));
}

__device__ inline bool aabb_hit(const float* b, V3 o, V3 invd, float tmin, float tmax) {
    float t0 = (b[0] - o.x) * invd.x, t1 = (b[3] - o.x) * invd.x;
    if (invd.x < 0.0f) { float t = t0; t0 = t1; t1 = t; }
    tmin = fmaxf(tmin, t0); tmax = fminf(tmax, t1);
    t0 = (b[1] - o.y) * invd.y; t1 = (b[4] - o.y) * invd.y;
    if (invd.y < 0.0f) { float t = t0; t0 = t1; t1 = t; }
    tmin = fmaxf(tmin, t0); tmax = fminf(tmax, t1);
    t0 = (b[2] - o.z) * invd.z; t1 = (b[5] - o.z) * invd.z;
    if (invd.z < 0.0f) { float t = t0; t0 = t1; t1 = t; }
    tmin = fmaxf(tmin, t0); tmax = fminf(tmax, t1);
    return tmax >= tmin;
}

__device__ inline float hit_sphere(const float* d, V3 o, V3 dir, float tmin, float tmax) {
    V3 c = v3(d[0], d[1], d[2]);
    float r = d[3];
    V3 oc = sub(o, c);
    float a = dot(dir, dir), half_b = dot(oc, dir), cc = dot(oc, oc) - r * r;
    float disc = half_b * half_b - a * cc;
    if (disc < 0.0f) return -1.0f;
    float sq = sqrtf(disc);
    float root = (-half_b - sq) / a;
    if (root < tmin || root > tmax) {
        root = (-half_b + sq) / a;
        if (root < tmin || root > tmax) return -1.0f;
    }
    return root;
}

__device__ inline float hit_tri(const float* d, V3 o, V3 dir, float tmin, float tmax) {
    V3 v0 = v3(d[0], d[1], d[2]), v1 = v3(d[3], d[4], d[5]), v2 = v3(d[6], d[7], d[8]);
    V3 e1 = sub(v1, v0), e2 = sub(v2, v0), pv = cross(dir, e2);
    float det = dot(e1, pv);
    if (fabsf(det) < 1e-8f) return -1.0f;
    float inv = 1.0f / det;
    V3 tv = sub(o, v0);
    float u = dot(tv, pv) * inv;
    if (u < 0.0f || u > 1.0f) return -1.0f;
    V3 qv = cross(tv, e1);
    float v = dot(dir, qv) * inv;
    if (v < 0.0f || u + v > 1.0f) return -1.0f;
    float t = dot(e2, qv) * inv;
    if (t < tmin || t > tmax) return -1.0f;
    return t;
}

// Closest hit over the BVH; returns the primitive index (or -1) and writes `t`.
__device__ int closest_hit(V3 ro, V3 dir, float tmin, float* t_out, const float* nbounds,
                           const unsigned int* nmeta, const unsigned int* pkind,
                           const float* pdata) {
    V3 invd = v3(1.0f / dir.x, 1.0f / dir.y, 1.0f / dir.z);
    float closest = 1e30f;
    int best = -1;
    bool dn[3] = {dir.x < 0.0f, dir.y < 0.0f, dir.z < 0.0f};
    int stack[64];
    int sp = 0, node = 0;
    while (true) {
        const float* b = &nbounds[node * 6];
        if (aabb_hit(b, ro, invd, tmin, closest)) {
            unsigned int np = nmeta[node * 3 + 1];
            if (np > 0) {
                unsigned int start = nmeta[node * 3];
                for (unsigned int k = 0; k < np; k++) {
                    int pi = start + k;
                    const float* d = &pdata[pi * 9];
                    float th = (pkind[pi] == 0) ? hit_sphere(d, ro, dir, tmin, closest)
                                                : hit_tri(d, ro, dir, tmin, closest);
                    if (th > 0.0f && th < closest) { closest = th; best = pi; }
                }
                if (sp == 0) break;
                node = stack[--sp];
            } else {
                unsigned int second = nmeta[node * 3], axis = nmeta[node * 3 + 2];
                int first = node + 1;
                if (dn[axis]) { stack[sp++] = first; node = (int)second; }
                else { stack[sp++] = (int)second; node = first; }
            }
        } else {
            if (sp == 0) break;
            node = stack[--sp];
        }
    }
    *t_out = closest;
    return best;
}

__device__ inline V3 normal_at(int best, V3 p, V3 dir, const unsigned int* pkind,
                               const float* pdata) {
    const float* d = &pdata[best * 9];
    V3 n;
    if (pkind[best] == 0) {
        n = normalize(sub(p, v3(d[0], d[1], d[2])));
    } else {
        V3 v0 = v3(d[0], d[1], d[2]), v1 = v3(d[3], d[4], d[5]), v2 = v3(d[6], d[7], d[8]);
        n = normalize(cross(sub(v1, v0), sub(v2, v0)));
    }
    if (dot(n, dir) > 0.0f) n = scale(n, -1.0f);
    return n;
}

__device__ inline V3 background(V3 dir, int bg_kind, const float* bg) {
    if (bg_kind == 0) return v3(bg[0], bg[1], bg[2]);
    float tt = 0.5f * (dir.y + 1.0f);
    return add(scale(v3(bg[0], bg[1], bg[2]), 1.0f - tt), scale(v3(bg[3], bg[4], bg[5]), tt));
}

extern "C" __global__ void pathtrace(float* out, int W, int H, int spp, int max_depth,
                                     unsigned int seed, const float* nbounds,
                                     const unsigned int* nmeta, const unsigned int* pkind,
                                     const float* pdata, const unsigned int* pmat,
                                     const float* mats, const float* cam, int bg_kind,
                                     const float* bg) {
    int px = blockIdx.x * blockDim.x + threadIdx.x;
    int py = blockIdx.y * blockDim.y + threadIdx.y;
    if (px >= W || py >= H) return;

    V3 origin = v3(cam[0], cam[1], cam[2]);
    V3 lower_left = v3(cam[3], cam[4], cam[5]);
    V3 horizontal = v3(cam[6], cam[7], cam[8]);
    V3 vertical = v3(cam[9], cam[10], cam[11]);

    unsigned int state = (px * 1973u) ^ (py * 9277u) ^ (seed * 26699u);
    state |= 1u;
    pcg(state);

    V3 sum = v3(0.0f, 0.0f, 0.0f);
    for (int s = 0; s < spp; s++) {
        float u = (px + randf(state)) / (float)W;
        float t = (H - 1 - py + randf(state)) / (float)H;
        V3 dir =
            normalize(sub(add(add(lower_left, scale(horizontal, u)), scale(vertical, t)), origin));
        V3 ro = origin;
        V3 thr = v3(1.0f, 1.0f, 1.0f);
        V3 L = v3(0.0f, 0.0f, 0.0f);

        for (int depth = 0; depth < max_depth; depth++) {
            float th;
            int best = closest_hit(ro, dir, 1e-3f, &th, nbounds, nmeta, pkind, pdata);
            if (best < 0) {
                L = add(L, mulv(thr, background(dir, bg_kind, bg)));
                break;
            }
            const float* m = &mats[pmat[best] * 8];
            int kind = (int)m[0];
            V3 emit = v3(m[4], m[5], m[6]);
            if (emit.x > 0.0f || emit.y > 0.0f || emit.z > 0.0f) L = add(L, mulv(thr, emit));
            if (kind == 2) break; // emissive: stop the path

            V3 p = add(ro, scale(dir, th));
            V3 n = normal_at(best, p, dir, pkind, pdata);
            thr = mulv(thr, v3(m[1], m[2], m[3])); // albedo

            if (depth >= 3) {
                float q = fmaxf(thr.x, fmaxf(thr.y, thr.z));
                if (randf(state) > q) break;
                thr = scale(thr, 1.0f / fmaxf(q, 1e-4f));
            }
            ro = add(p, scale(n, 1e-4f));
            dir = cosine_dir(n, randf(state), randf(state));
        }
        sum = add(sum, L);
    }

    sum = scale(sum, 1.0f / (float)spp);
    int ci = (py * W + px) * 3;
    out[ci + 0] = sum.x;
    out[ci + 1] = sum.y;
    out[ci + 2] = sum.z;
}
