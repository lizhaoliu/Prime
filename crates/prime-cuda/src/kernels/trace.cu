// Phase B GPU kernel: iterative BVH traversal for primary-ray visibility.
//
// This mirrors the CPU `Bvh::hit` exactly — same flat node layout, same
// near/far ordering, same Möller-Trumbore / ray-sphere math — so the closest-hit
// distance matches the CPU renderer (validated pixel-wise on the host).
//
// Buffers (host-packed, see prime_core::bvh::FlatBvh):
//   nbounds: 6 f32/node  -> min.xyz, max.xyz
//   nmeta:   3 u32/node  -> offset, n_prims, axis  (leaf iff n_prims > 0)
//   pkind:   1 u32/prim  -> 0 = sphere, 1 = triangle
//   pdata:   9 f32/prim  -> sphere: cx,cy,cz,r,...  | triangle: v0,v1,v2
//   cam:     12 f32      -> origin(3), lower_left(3), horizontal(3), vertical(3)
//   out_color: W*H*3 f32 (linear RGB, row 0 = top)
//   out_t:     W*H   f32 (closest hit distance, or -1 on miss)

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
__device__ inline V3 cross(V3 a, V3 b) {
    return v3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
}
__device__ inline V3 normalize(V3 a) {
    float l = sqrtf(dot(a, a));
    return l > 0.0f ? scale(a, 1.0f / l) : a;
}

__device__ inline bool aabb_hit(const float* b, V3 o, V3 invd, float tmin, float tmax) {
    float t0 = (b[0] - o.x) * invd.x, t1 = (b[3] - o.x) * invd.x;
    if (invd.x < 0.0f) {
        float t = t0;
        t0 = t1;
        t1 = t;
    }
    tmin = fmaxf(tmin, t0);
    tmax = fminf(tmax, t1);
    t0 = (b[1] - o.y) * invd.y;
    t1 = (b[4] - o.y) * invd.y;
    if (invd.y < 0.0f) {
        float t = t0;
        t0 = t1;
        t1 = t;
    }
    tmin = fmaxf(tmin, t0);
    tmax = fminf(tmax, t1);
    t0 = (b[2] - o.z) * invd.z;
    t1 = (b[5] - o.z) * invd.z;
    if (invd.z < 0.0f) {
        float t = t0;
        t0 = t1;
        t1 = t;
    }
    tmin = fmaxf(tmin, t0);
    tmax = fminf(tmax, t1);
    return tmax >= tmin;
}

__device__ inline float hit_sphere(const float* d, V3 o, V3 dir, float tmin, float tmax) {
    V3 c = v3(d[0], d[1], d[2]);
    float r = d[3];
    V3 oc = sub(o, c);
    float a = dot(dir, dir);
    float half_b = dot(oc, dir);
    float cc = dot(oc, oc) - r * r;
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
    V3 e1 = sub(v1, v0), e2 = sub(v2, v0);
    V3 pv = cross(dir, e2);
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

extern "C" __global__ void trace(float* out_color, float* out_t, int W, int H,
                                 const float* nbounds, const unsigned int* nmeta,
                                 const unsigned int* pkind, const float* pdata, const float* cam) {
    int px = blockIdx.x * blockDim.x + threadIdx.x;
    int py = blockIdx.y * blockDim.y + threadIdx.y;
    if (px >= W || py >= H) return;

    V3 origin = v3(cam[0], cam[1], cam[2]);
    V3 lower_left = v3(cam[3], cam[4], cam[5]);
    V3 horizontal = v3(cam[6], cam[7], cam[8]);
    V3 vertical = v3(cam[9], cam[10], cam[11]);
    float s = (px + 0.5f) / (float)W;
    float t = (H - 1 - py + 0.5f) / (float)H;
    V3 dir = normalize(sub(add(add(lower_left, scale(horizontal, s)), scale(vertical, t)), origin));
    V3 invd = v3(1.0f / dir.x, 1.0f / dir.y, 1.0f / dir.z);

    const float TMIN = 1e-3f;
    float closest = 1e30f;
    int best = -1;
    bool dir_neg[3] = {dir.x < 0.0f, dir.y < 0.0f, dir.z < 0.0f};

    int stack[64];
    int sp = 0;
    int node = 0;
    while (true) {
        const float* b = &nbounds[node * 6];
        if (aabb_hit(b, origin, invd, TMIN, closest)) {
            unsigned int n_prims = nmeta[node * 3 + 1];
            if (n_prims > 0) {
                unsigned int start = nmeta[node * 3 + 0];
                for (unsigned int k = 0; k < n_prims; k++) {
                    int pi = start + k;
                    const float* d = &pdata[pi * 9];
                    float th = (pkind[pi] == 0) ? hit_sphere(d, origin, dir, TMIN, closest)
                                                : hit_tri(d, origin, dir, TMIN, closest);
                    if (th > 0.0f && th < closest) {
                        closest = th;
                        best = pi;
                    }
                }
                if (sp == 0) break;
                node = stack[--sp];
            } else {
                unsigned int second = nmeta[node * 3 + 0];
                unsigned int axis = nmeta[node * 3 + 2];
                int first = node + 1;
                if (dir_neg[axis]) {
                    stack[sp++] = first;
                    node = (int)second;
                } else {
                    stack[sp++] = (int)second;
                    node = first;
                }
            }
        } else {
            if (sp == 0) break;
            node = stack[--sp];
        }
    }

    int ci = (py * W + px) * 3;
    if (best >= 0) {
        V3 p = add(origin, scale(dir, closest));
        const float* d = &pdata[best * 9];
        V3 n;
        if (pkind[best] == 0) {
            n = normalize(sub(p, v3(d[0], d[1], d[2])));
        } else {
            V3 v0 = v3(d[0], d[1], d[2]), v1 = v3(d[3], d[4], d[5]), v2 = v3(d[6], d[7], d[8]);
            n = normalize(cross(sub(v1, v0), sub(v2, v0)));
        }
        if (dot(n, dir) > 0.0f) n = scale(n, -1.0f); // face the viewer
        out_color[ci + 0] = 0.5f * (n.x + 1.0f);
        out_color[ci + 1] = 0.5f * (n.y + 1.0f);
        out_color[ci + 2] = 0.5f * (n.z + 1.0f);
        out_t[py * W + px] = closest;
    } else {
        float tt = 0.5f * (dir.y + 1.0f);
        out_color[ci + 0] = (1.0f - tt) + tt * 0.5f;
        out_color[ci + 1] = (1.0f - tt) + tt * 0.7f;
        out_color[ci + 2] = (1.0f - tt) + tt * 1.0f;
        out_t[py * W + px] = -1.0f;
    }
}
