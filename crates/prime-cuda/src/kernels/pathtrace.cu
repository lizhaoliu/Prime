// Phase C (increment 3) GPU path tracer: the full material set — Lambertian,
// GGX microfacet Metal (mirror + rough), and Dielectric (glass) — plus emissive,
// with next-event estimation and MIS. Mirrors the CPU integrator + material BSDFs
// (Trowbridge-Reitz D, height-correlated Smith G, Schlick Fresnel, VNDF sampling
// per Heitz 2018) so the GPU image converges to the CPU reference.
//
// Buffers: FlatBvh (nbounds/nmeta/pkind/pdata/pmat) + material table `mats`
// (8 f32/material: kind, albedo.xyz, emit.xyz, param) + `light_prims` + camera
// `cam` (12 f32) + background `bg`. Material kinds: 0 Lambertian, 1 Metal
// (param = roughness), 2 Emissive, 3 Dielectric (param = ior).

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
__device__ inline V3 neg(V3 a) { return v3(-a.x, -a.y, -a.z); }
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
__device__ inline float clampf(float x, float lo, float hi) { return fminf(fmaxf(x, lo), hi); }
__device__ inline V3 reflect(V3 v, V3 n) { return sub(v, scale(n, 2.0f * dot(v, n))); }

#define PI 3.14159265359f
#define INV_PI 0.3183098862f
#define TWO_PI 6.2831853072f
#define MIRROR_ROUGHNESS 0.02f

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
    float a2 = a * a, b2 = b * b, d = a2 + b2;
    return d > 0.0f ? a2 / d : 0.0f;
}

// --- GGX microfacet helpers (match prime-core material.rs) ------------------
__device__ inline float ggx_alpha(float rough) { return clampf(rough, 1e-3f, 1.0f); }
__device__ inline float ggx_d(float nh, float a2) {
    float x = nh * nh * (a2 - 1.0f) + 1.0f;
    return a2 / (PI * x * x);
}
__device__ inline float smith_lambda(float c, float a2) {
    float c2 = fmaxf(c * c, 1e-8f);
    float tan2 = (1.0f - c2) / c2;
    return 0.5f * (-1.0f + sqrtf(1.0f + a2 * tan2));
}
__device__ inline float smith_g1(float c, float a2) { return 1.0f / (1.0f + smith_lambda(c, a2)); }
__device__ inline float smith_g2(float co, float cl, float a2) {
    return 1.0f / (1.0f + smith_lambda(co, a2) + smith_lambda(cl, a2));
}
__device__ inline V3 fresnel_schlick(float c, V3 f0) {
    float m = clampf(1.0f - c, 0.0f, 1.0f);
    float m5 = m * m * m * m * m;
    return add(f0, scale(sub(v3(1.0f, 1.0f, 1.0f), f0), m5));
}
__device__ inline float schlick_reflectance(float cosine, float eta) {
    float r0 = (1.0f - eta) / (1.0f + eta);
    r0 = r0 * r0;
    float m = 1.0f - cosine;
    return r0 + (1.0f - r0) * m * m * m * m * m;
}
__device__ inline V3 refract(V3 uv, V3 n, float eta) {
    float cos_theta = fminf(dot(neg(uv), n), 1.0f);
    V3 perp = scale(add(uv, scale(n, cos_theta)), eta);
    V3 par = scale(n, -sqrtf(fabsf(1.0f - dot(perp, perp))));
    return add(perp, par);
}
// VNDF sample (Heitz 2018): `ve` local view dir (z = normal), returns local h.
__device__ V3 sample_ggx_vndf(V3 ve, float alpha, float u1, float u2) {
    V3 vh = normalize(v3(alpha * ve.x, alpha * ve.y, ve.z));
    float lensq = vh.x * vh.x + vh.y * vh.y;
    V3 t1 = lensq > 1e-12f ? scale(v3(-vh.y, vh.x, 0.0f), 1.0f / sqrtf(lensq)) : v3(1.0f, 0.0f, 0.0f);
    V3 t2 = cross(vh, t1);
    float r = sqrtf(u1), phi = TWO_PI * u2;
    float p1 = r * cosf(phi), p2 = r * sinf(phi);
    float s = 0.5f * (1.0f + vh.z);
    p2 = (1.0f - s) * sqrtf(fmaxf(0.0f, 1.0f - p1 * p1)) + s * p2;
    float pz = sqrtf(fmaxf(0.0f, 1.0f - p1 * p1 - p2 * p2));
    V3 nh = add(add(scale(t1, p1), scale(t2, p2)), scale(vh, pz));
    return normalize(v3(alpha * nh.x, alpha * nh.y, fmaxf(nh.z, 0.0f)));
}

__device__ inline bool is_specular(int kind, float rough) {
    return (kind == 1 && rough <= MIRROR_ROUGHNESS) || kind == 3;
}

// BSDF value f(wo, wi) for non-specular lobes (Lambertian, rough Metal).
__device__ V3 bsdf_eval(int kind, V3 albedo, float rough, V3 n, V3 wo, V3 wi) {
    if (kind == 0) {
        return (dot(wi, n) > 0.0f && dot(wo, n) > 0.0f) ? scale(albedo, INV_PI) : v3(0, 0, 0);
    }
    if (kind == 1 && rough > MIRROR_ROUGHNESS) {
        float no = dot(wo, n), nl = dot(wi, n);
        if (no <= 0.0f || nl <= 0.0f) return v3(0, 0, 0);
        V3 h = normalize(add(wo, wi));
        float nh = fmaxf(dot(n, h), 0.0f), vh = fmaxf(dot(wo, h), 0.0f);
        float a2 = ggx_alpha(rough);
        a2 = a2 * a2;
        V3 fr = fresnel_schlick(vh, albedo);
        return scale(fr, ggx_d(nh, a2) * smith_g2(no, nl, a2) / (4.0f * no * nl));
    }
    return v3(0, 0, 0);
}
__device__ float bsdf_pdf(int kind, float rough, V3 n, V3 wo, V3 wi) {
    if (kind == 0) {
        float c = dot(wi, n);
        return c > 0.0f ? c * INV_PI : 0.0f;
    }
    if (kind == 1 && rough > MIRROR_ROUGHNESS) {
        float no = dot(wo, n), nl = dot(wi, n);
        if (no <= 0.0f || nl <= 0.0f) return 0.0f;
        V3 h = normalize(add(wo, wi));
        float nh = fmaxf(dot(n, h), 0.0f);
        float a2 = ggx_alpha(rough);
        a2 = a2 * a2;
        return ggx_d(nh, a2) * smith_g1(no, a2) / (4.0f * no);
    }
    return 0.0f;
}

struct Bsdf {
    V3 wi, f;
    float pdf;
    int specular; // 1 = delta lobe (mirror/glass)
    int valid;
};
// Importance-sample an outgoing direction.
__device__ Bsdf bsdf_sample(int kind, V3 albedo, float rough, float ior, V3 n, int front, V3 wo,
                            unsigned int& st) {
    Bsdf r;
    r.valid = 0;
    r.specular = 0;
    if (kind == 0) {
        V3 wi = cosine_dir(n, randf(st), randf(st));
        float c = dot(wi, n);
        if (c <= 0.0f) return r;
        r.wi = wi;
        r.f = scale(albedo, INV_PI);
        r.pdf = c * INV_PI;
        r.valid = 1;
        return r;
    }
    if (kind == 1) {
        if (rough <= MIRROR_ROUGHNESS) {
            V3 wi = reflect(neg(wo), n);
            if (dot(wi, n) <= 0.0f) return r;
            r.wi = normalize(wi);
            r.f = albedo;
            r.pdf = 1.0f;
            r.specular = 1;
            r.valid = 1;
            return r;
        }
        float alpha = ggx_alpha(rough);
        V3 a = fabsf(n.x) > 0.9f ? v3(0.0f, 1.0f, 0.0f) : v3(1.0f, 0.0f, 0.0f);
        V3 t1 = normalize(cross(a, n)), t2 = cross(n, t1);
        V3 wo_l = v3(dot(wo, t1), dot(wo, t2), dot(wo, n));
        if (wo_l.z <= 0.0f) return r;
        V3 nh = sample_ggx_vndf(wo_l, alpha, randf(st), randf(st));
        V3 h = add(add(scale(t1, nh.x), scale(t2, nh.y)), scale(n, nh.z));
        V3 wi = reflect(neg(wo), h);
        if (dot(wi, n) <= 0.0f) return r;
        float pdf = bsdf_pdf(1, rough, n, wo, wi);
        if (pdf <= 0.0f) return r;
        r.wi = wi;
        r.f = bsdf_eval(1, albedo, rough, n, wo, wi);
        r.pdf = pdf;
        r.valid = 1;
        return r;
    }
    // kind == 3: dielectric.
    float eta = front ? 1.0f / ior : ior;
    V3 incoming = neg(wo);
    float cos_theta = fminf(dot(wo, n), 1.0f);
    float sin_theta = sqrtf(fmaxf(0.0f, 1.0f - cos_theta * cos_theta));
    int cannot = (eta * sin_theta > 1.0f);
    V3 wi = (cannot || schlick_reflectance(cos_theta, eta) > randf(st)) ? reflect(incoming, n)
                                                                        : refract(incoming, n, eta);
    r.wi = normalize(wi);
    r.f = v3(1.0f, 1.0f, 1.0f);
    r.pdf = 1.0f;
    r.specular = 1;
    r.valid = 1;
    return r;
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
__device__ bool occluded(V3 ro, V3 dir, float tmin, float tmax, const float* nbounds,
                         const unsigned int* nmeta, const unsigned int* pkind, const float* pdata) {
    float t;
    int best = closest_hit(ro, dir, tmin, &t, nbounds, nmeta, pkind, pdata);
    return best >= 0 && t < tmax;
}

// Outward geometric normal of primitive `best` at point `p` (not face-forwarded).
__device__ inline V3 geom_normal(int best, V3 p, const unsigned int* pkind, const float* pdata) {
    const float* d = &pdata[best * 9];
    if (pkind[best] == 0) return normalize(sub(p, v3(d[0], d[1], d[2])));
    V3 v0 = v3(d[0], d[1], d[2]), v1 = v3(d[3], d[4], d[5]), v2 = v3(d[6], d[7], d[8]);
    return normalize(cross(sub(v1, v0), sub(v2, v0)));
}

__device__ inline float prim_area(int pi, const unsigned int* pkind, const float* pdata) {
    const float* d = &pdata[pi * 9];
    if (pkind[pi] == 0) {
        float r = d[3];
        return 4.0f * PI * r * r;
    }
    V3 v0 = v3(d[0], d[1], d[2]), v1 = v3(d[3], d[4], d[5]), v2 = v3(d[6], d[7], d[8]);
    return 0.5f * length(cross(sub(v1, v0), sub(v2, v0)));
}
__device__ void sample_prim(int pi, float r1, float r2, const unsigned int* pkind,
                            const float* pdata, V3* q, V3* nl, float* area) {
    const float* d = &pdata[pi * 9];
    if (pkind[pi] == 0) {
        float z = 1.0f - 2.0f * r1, rr = sqrtf(fmaxf(0.0f, 1.0f - z * z)), phi = TWO_PI * r2;
        V3 dn = v3(rr * cosf(phi), rr * sinf(phi), z);
        float r = d[3];
        *q = add(v3(d[0], d[1], d[2]), scale(dn, r));
        *nl = dn;
        *area = 4.0f * PI * r * r;
    } else {
        V3 v0 = v3(d[0], d[1], d[2]), v1 = v3(d[3], d[4], d[5]), v2 = v3(d[6], d[7], d[8]);
        V3 e1 = sub(v1, v0), e2 = sub(v2, v0);
        float su = sqrtf(r1), b1 = 1.0f - su, b2 = r2 * su;
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

// Texture coordinates at the hit: analytic for spheres, barycentric-interpolated
// per-vertex UVs for triangles (matching the CPU's Triangle::hit).
__device__ void hit_uv(int best, V3 p, const unsigned int* pkind, const float* pdata,
                       const float* prim_uv, float* uo, float* vo) {
    if (pkind[best] == 0) {
        const float* d = &pdata[best * 9];
        V3 o = scale(sub(p, v3(d[0], d[1], d[2])), 1.0f / d[3]);
        float theta = acosf(clampf(-o.y, -1.0f, 1.0f));
        float phi = atan2f(-o.z, o.x) + PI;
        *uo = phi / TWO_PI;
        *vo = theta / PI;
        return;
    }
    const float* dv = &pdata[best * 9];
    V3 v0 = v3(dv[0], dv[1], dv[2]), v1 = v3(dv[3], dv[4], dv[5]), v2 = v3(dv[6], dv[7], dv[8]);
    V3 e1 = sub(v1, v0), e2 = sub(v2, v0), ep = sub(p, v0);
    float d11 = dot(e1, e1), d12 = dot(e1, e2), d22 = dot(e2, e2);
    float dp1 = dot(ep, e1), dp2 = dot(ep, e2);
    float denom = d11 * d22 - d12 * d12;
    float inv = denom != 0.0f ? 1.0f / denom : 0.0f;
    float bu = (d22 * dp1 - d12 * dp2) * inv; // weight of v1
    float bv = (d11 * dp2 - d12 * dp1) * inv; // weight of v2
    float bw = 1.0f - bu - bv;                // weight of v0
    const float* u = &prim_uv[best * 6];
    *uo = bw * u[0] + bu * u[2] + bv * u[4];
    *vo = bw * u[1] + bu * u[3] + bv * u[5];
}

// Albedo from the per-material texture descriptor (8 f32: kind, c.xyz, d.xyz,
// scale). kind 0 = constant color c; kind 1 = checkerboard of c (even) / d (odd).
__device__ V3 sample_albedo(int mat, float u, float v, const float* albedo_tex) {
    const float* a = &albedo_tex[mat * 8];
    if ((int)a[0] == 1) {
        long parity = (long)floorf(u * a[7]) + (long)floorf(v * a[7]);
        if ((((parity % 2) + 2) % 2) == 0) return v3(a[1], a[2], a[3]);
        return v3(a[4], a[5], a[6]);
    }
    return v3(a[1], a[2], a[3]);
}

extern "C" __global__ void pathtrace(float* out, int W, int H, int spp, int max_depth,
                                     unsigned int seed, const float* nbounds,
                                     const unsigned int* nmeta, const unsigned int* pkind,
                                     const float* pdata, const float* prim_uv,
                                     const unsigned int* pmat, const float* mats,
                                     const float* albedo_tex, const unsigned int* light_prims,
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
        bool specular = true;

        for (int depth = 0; depth < max_depth; depth++) {
            float t_hit;
            int best = closest_hit(ro, dir, 1e-3f, &t_hit, nbounds, nmeta, pkind, pdata);
            if (best < 0) {
                L = add(L, mulv(thr, background(dir, bg_kind, bg)));
                break;
            }
            const float* m = &mats[pmat[best] * 8];
            int kind = (int)m[0];
            V3 emit = v3(m[4], m[5], m[6]);
            V3 p = add(ro, scale(dir, t_hit));

            if (emit.x > 0.0f || emit.y > 0.0f || emit.z > 0.0f) {
                if (specular || n_lights == 0) {
                    L = add(L, mulv(thr, emit));
                } else {
                    V3 gn = geom_normal(best, p, pkind, pdata);
                    float area = prim_area(best, pkind, pdata);
                    float cosl = fabsf(dot(gn, dir));
                    float lpdf = (cosl > 1e-6f && area > 0.0f)
                                     ? (t_hit * t_hit) / (n_lights * area * cosl)
                                     : 0.0f;
                    float w = (lpdf > 0.0f) ? power_heuristic(prev_pdf, lpdf) : 1.0f;
                    L = add(L, scale(mulv(thr, emit), w));
                }
                break;
            }

            float rough = m[7]; // roughness (Metal) or ior (Dielectric)
            V3 outward = geom_normal(best, p, pkind, pdata);
            int front = dot(dir, outward) < 0.0f;
            V3 n = front ? outward : neg(outward);
            V3 wo = neg(dir);
            float uu, vv;
            hit_uv(best, p, pkind, pdata, prim_uv, &uu, &vv);
            V3 albedo = sample_albedo(pmat[best], uu, vv, albedo_tex);

            // Next-event estimation (only for non-delta BSDFs).
            if (!is_specular(kind, rough) && n_lights > 0) {
                int li = (int)(randf(state) * n_lights);
                if (li >= n_lights) li = n_lights - 1;
                int lp = (int)light_prims[li];
                V3 q, nl;
                float area;
                sample_prim(lp, randf(state), randf(state), pkind, pdata, &q, &nl, &area);
                V3 le = v3(mats[pmat[lp] * 8 + 4], mats[pmat[lp] * 8 + 5], mats[pmat[lp] * 8 + 6]);
                V3 to = sub(q, p);
                float dist2 = dot(to, to), dist = sqrtf(dist2);
                V3 wi = scale(to, 1.0f / dist);
                float coss = dot(n, wi), cosl = fabsf(dot(nl, wi));
                if (coss > 0.0f && cosl > 1e-6f && area > 0.0f) {
                    float lpdf = dist2 / (n_lights * area * cosl);
                    if (lpdf > 0.0f &&
                        !occluded(p, wi, 1e-3f, dist * (1.0f - 1e-3f), nbounds, nmeta, pkind,
                                  pdata)) {
                        V3 f = bsdf_eval(kind, albedo, rough, n, wo, wi);
                        float bpdf = bsdf_pdf(kind, rough, n, wo, wi);
                        float w = power_heuristic(lpdf, bpdf);
                        L = add(L, scale(mulv(mulv(thr, f), le), coss * w / lpdf));
                    }
                }
            }

            // BSDF bounce.
            Bsdf bs = bsdf_sample(kind, albedo, rough, m[7], n, front, wo, state);
            if (!bs.valid) break;
            if (bs.specular) {
                thr = mulv(thr, bs.f);
                specular = true;
                prev_pdf = 0.0f;
            } else {
                if (bs.pdf <= 0.0f) break;
                float c = fabsf(dot(bs.wi, n));
                thr = mulv(thr, scale(bs.f, c / bs.pdf));
                specular = false;
                prev_pdf = bs.pdf;
            }

            if (depth >= 3) {
                float q = fmaxf(thr.x, fmaxf(thr.y, thr.z));
                if (randf(state) > q) break;
                thr = scale(thr, 1.0f / fmaxf(q, 1e-4f));
            }
            ro = p;
            dir = bs.wi;
        }
        sum = add(sum, L);
    }

    sum = scale(sum, 1.0f / (float)spp);
    int ci = (py * W + px) * 3;
    out[ci + 0] = sum.x;
    out[ci + 1] = sum.y;
    out[ci + 2] = sum.z;
}
