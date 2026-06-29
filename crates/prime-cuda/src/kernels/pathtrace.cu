// Phase C (increment 2) GPU path tracer: Lambertian + emissive with
// next-event estimation (NEE) and multiple importance sampling (MIS), matching
// the CPU integrator's structure (power heuristic, area-measure light pdf). This
// dramatically lowers noise versus the increment-1 pure path tracer.
//
// Sampling is plain white noise (per-pixel/per-sample PCG); the GPU image still
// converges to the CPU reference (validated by RMSE on the host), now much
// faster per sample thanks to NEE.
//
// Buffers: FlatBvh (nbounds/nmeta/pkind/pdata/pmat) + material table `mats`
// (8 f32/material: kind, albedo.xyz, emit.xyz, param) + `light_prims` (indices
// of emissive primitives) + camera frame `cam` (12 f32) + background `bg`.

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
__device__ inline float length(V3 a) { return sqrtf(dot(a, a)); }
__device__ inline V3 cross(V3 a, V3 b) {
    return v3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
}
__device__ inline V3 normalize(V3 a) {
    float l = length(a);
    return l > 0.0f ? scale(a, 1.0f / l) : a;
}

#define INV_PI 0.3183098862f
#define TWO_PI 6.2831853072f

__device__ inline unsigned int pcg(unsigned int& state) {
    state = state * 747796405u + 2891336453u;
    unsigned int word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}
__device__ inline float randf(unsigned int& state) {
    return (pcg(state) >> 8) * (1.0f / 16777216.0f);
}

__device__ inline V3 cosine_dir(V3 n, float r1, float r2) {
    float phi = TWO_PI * r1;
    float r = sqrtf(r2);
    float x = r * cosf(phi), y = r * sinf(phi), z = sqrtf(fmaxf(0.0f, 1.0f - r2));
    V3 a = fabsf(n.x) > 0.9f ? v3(0.0f, 1.0f, 0.0f) : v3(1.0f, 0.0f, 0.0f);
    V3 t = normalize(cross(a, n));
    V3 b = cross(n, t);
    return normalize(add(add(scale(t, x), scale(b, y)), scale(n, z)));
}

__device__ inline float power_heuristic(float a, float b) {
    float a2 = a * a, b2 = b * b;
    float d = a2 + b2;
    return d > 0.0f ? a2 / d : 0.0f;
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

// Shadow query: is anything within [tmin, tmax)? Delegates to the validated
// closest-hit traversal (a dedicated any-hit traversal is a later optimization).
__device__ bool occluded(V3 ro, V3 dir, float tmin, float tmax, const float* nbounds,
                         const unsigned int* nmeta, const unsigned int* pkind, const float* pdata) {
    float t;
    int best = closest_hit(ro, dir, tmin, &t, nbounds, nmeta, pkind, pdata);
    return best >= 0 && t < tmax;
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

__device__ inline float prim_area(int pi, const unsigned int* pkind, const float* pdata) {
    const float* d = &pdata[pi * 9];
    if (pkind[pi] == 0) {
        float r = d[3];
        return 4.0f * 3.14159265f * r * r;
    }
    V3 v0 = v3(d[0], d[1], d[2]), v1 = v3(d[3], d[4], d[5]), v2 = v3(d[6], d[7], d[8]);
    return 0.5f * length(cross(sub(v1, v0), sub(v2, v0)));
}

// Uniformly sample a point on primitive `pi`; writes point, surface normal, area.
__device__ void sample_prim(int pi, float r1, float r2, const unsigned int* pkind,
                            const float* pdata, V3* q, V3* nl, float* area) {
    const float* d = &pdata[pi * 9];
    if (pkind[pi] == 0) {
        float z = 1.0f - 2.0f * r1;
        float rr = sqrtf(fmaxf(0.0f, 1.0f - z * z));
        float phi = TWO_PI * r2;
        V3 dn = v3(rr * cosf(phi), rr * sinf(phi), z);
        float r = d[3];
        *q = add(v3(d[0], d[1], d[2]), scale(dn, r));
        *nl = dn;
        *area = 4.0f * 3.14159265f * r * r;
    } else {
        V3 v0 = v3(d[0], d[1], d[2]), v1 = v3(d[3], d[4], d[5]), v2 = v3(d[6], d[7], d[8]);
        V3 e1 = sub(v1, v0), e2 = sub(v2, v0);
        float su = sqrtf(r1);
        float b1 = 1.0f - su, b2 = r2 * su;
        *q = add(v0, add(scale(e1, b1), scale(e2, b2)));
        V3 cr = cross(e1, e2);
        float l = length(cr);
        *nl = scale(cr, 1.0f / fmaxf(l, 1e-12f));
        *area = 0.5f * l;
    }
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
                                     const float* mats, const unsigned int* light_prims,
                                     int n_lights, const float* cam, int bg_kind,
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
        float prev_pdf = 0.0f;
        bool specular = true; // first hit on a light counts at full weight

        for (int depth = 0; depth < max_depth; depth++) {
            float t_hit;
            int best = closest_hit(ro, dir, 1e-3f, &t_hit, nbounds, nmeta, pkind, pdata);
            if (best < 0) {
                L = add(L, mulv(thr, background(dir, bg_kind, bg)));
                break;
            }
            const float* m = &mats[pmat[best] * 8];
            V3 emit = v3(m[4], m[5], m[6]);
            V3 p = add(ro, scale(dir, t_hit));

            if (emit.x > 0.0f || emit.y > 0.0f || emit.z > 0.0f) {
                // Hit an emitter: weight against the light-sampling strategy (MIS).
                if (specular || n_lights == 0) {
                    L = add(L, mulv(thr, emit));
                } else {
                    V3 n = normal_at(best, p, dir, pkind, pdata);
                    float area = prim_area(best, pkind, pdata);
                    float cosl = fabsf(dot(n, dir));
                    float lpdf =
                        (cosl > 1e-6f && area > 0.0f) ? (t_hit * t_hit) / (n_lights * area * cosl)
                                                      : 0.0f;
                    float w = (lpdf > 0.0f) ? power_heuristic(prev_pdf, lpdf) : 1.0f;
                    L = add(L, scale(mulv(thr, emit), w));
                }
                break; // emitter does not scatter
            }

            // Lambertian surface.
            V3 n = normal_at(best, p, dir, pkind, pdata);
            V3 albedo = v3(m[1], m[2], m[3]);

            // Next-event estimation: sample a light directly.
            if (n_lights > 0) {
                int li = (int)(randf(state) * n_lights);
                if (li >= n_lights) li = n_lights - 1;
                int lp = (int)light_prims[li];
                V3 q, nl;
                float area;
                sample_prim(lp, randf(state), randf(state), pkind, pdata, &q, &nl, &area);
                const float* lm = &mats[pmat[lp] * 8];
                V3 le = v3(lm[4], lm[5], lm[6]);
                V3 to = sub(q, p);
                float dist2 = dot(to, to);
                float dist = sqrtf(dist2);
                V3 wi = scale(to, 1.0f / dist);
                float coss = dot(n, wi);
                float cosl = fabsf(dot(nl, wi));
                if (coss > 0.0f && cosl > 1e-6f && area > 0.0f) {
                    float lpdf = dist2 / (n_lights * area * cosl);
                    // Shadow ray from p (skip self via tmin) stopping just short of
                    // the light, matching the CPU. Offsetting the origin instead
                    // would shorten the ray and make the light self-occlude.
                    if (lpdf > 0.0f &&
                        !occluded(p, wi, 1e-3f, dist * (1.0f - 1e-3f), nbounds, nmeta, pkind,
                                  pdata)) {
                        float bpdf = coss * INV_PI;
                        float w = power_heuristic(lpdf, bpdf);
                        float k = INV_PI * coss * w / lpdf;
                        L = add(L, scale(mulv(mulv(thr, albedo), le), k));
                    }
                }
            }

            // BSDF bounce (cosine-weighted Lambertian).
            thr = mulv(thr, albedo);
            if (depth >= 3) {
                float q = fmaxf(thr.x, fmaxf(thr.y, thr.z));
                if (randf(state) > q) break;
                thr = scale(thr, 1.0f / fmaxf(q, 1e-4f));
            }
            ro = add(p, scale(n, 1e-3f));
            V3 nd = cosine_dir(n, randf(state), randf(state));
            prev_pdf = fmaxf(dot(n, nd), 0.0f) * INV_PI;
            specular = false;
            dir = nd;
        }
        sum = add(sum, L);
    }

    sum = scale(sum, 1.0f / (float)spp);
    int ci = (py * W + px) * 3;
    out[ci + 0] = sum.x;
    out[ci + 1] = sum.y;
    out[ci + 2] = sum.z;
}
