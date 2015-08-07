package prime.physics;

import prime.math.Vec3f;

import static prime.math.MathUtils.dot;

public class OrenNayerModel extends Material {
  private float sigma = 50; // gaussian distribution parameter

  public void setSigma(float sigma) {
    this.sigma = sigma;
  }

  public float getSigma() {
    return sigma;
  }

  @Override
  public void brdf(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f outDir, Color3f dest) {
    // TODO Auto-generated method stub
    float sigma2 = sigma * sigma;
    float A = 1 - sigma2 / (2 * (sigma2 + 0.33f));
    float B = 0.45f * sigma2 / (sigma2 + 0.09f);
    float cosi = cosThetaAbsoluteValue(normal, inDir), sini = sinTheta(normal, inDir);
    float coso = cosThetaAbsoluteValue(normal, outDir), sino = sinTheta(normal, outDir);
    float sinalpha, tanbeta;
    if (cosi > coso) {
      sinalpha = sino;
      tanbeta = sini / cosi;
    } else {
      sinalpha = sini;
      tanbeta = sino / coso;
    }
    float factor = INV_PI * (A + B * Math.max(0, cosi * coso + sini * sino) * sinalpha * tanbeta);
    dest.set(reflectance);
    dest.multiply(factor);
  }

  @Override
  public float samplingReflectionDirection(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f dest) {
    do {
      dest.set(2 * (float) Math.random() - 1, 2 * (float) Math.random() - 1, 2 * (float) Math.random() - 1);
    } while (dot(dest, normal) < 0);
    dest.normalize();
    return INV_2PI;
  }

  @Override
  public float samplingTransmissionDirection(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f dest) {
    return 0.0f;
  }

  @Override
  public void btdf(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f outDir, Color3f dest) {
  }

}
