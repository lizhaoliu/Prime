package prime.physics;

import prime.math.Vec3f;

public class SchlickModel extends Material {
  private float f0; // specular reflection at normal incidence
  private float sigma; // roughness factor(0 : perfectly smooth; 1 : ideal diffuse
  private float psi; // isotropy factor(0 : perfect anisotropic; 1 : isotropic

  @Override
  public float samplingReflectionDirection(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f dest) {
    return 1.0f;
  }

  @Override
  public float samplingTransmissionDirection(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f dest) {
    return 1.0f;
  }

  @Override
  public void brdf(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f outDir, Color3f dest) {
    Vec3f H = Vec3f.add(inDir, outDir);
    H.normalize();
    float u = Vec3f.dot(outDir, H);
    float t = Vec3f.dot(normal, H);
    float v = Vec3f.dot(outDir, normal);
    float vdot = -Vec3f.dot(inDir, normal);
    float w = (float) (Math.random());
    float g = 4 * sigma * (1 - sigma);
    float d = (sigma < 0.5f ? 0.0f : 1 - g);
    float s = (sigma < 0.5f ? 1 - g : 0.0f);

    float factor = (float) (S(u) * (d / Math.PI + g * D(t, v, vdot, w) + s));
    dest.set(factor, factor, factor);
  }

  private float S(float u) {
    return f0 + (float) ((1 - f0) * Math.pow(1 - u, 5));
  }

  private float Z(float t) {
    float div = (1 - sigma * t - t * t);
    return sigma / (div * div);
  }

  private float D(float t, float v, float vdot, float w) {
    return (float) ((G(v) * G(vdot) * Z(t) * A(w) + 1 - G(v) * G(vdot)) / (4 * Math.PI * v * vdot));
  }

  private float A(float w) {
    return (float) (Math.sqrt(psi / (psi * psi * (1 - w * w) + w * w)));
  }

  private float G(float v) {
    return (v / (sigma - sigma * v + v));
  }

  @Override
  public void btdf(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f outDir, Color3f dest) {}
}
