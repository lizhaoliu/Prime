package prime.physics;

import prime.math.Vec3f;

public abstract class SparrowTorranceModel extends Material {
  protected MicrofacetDistribution d;

  public void setMicrofacetDistribution(MicrofacetDistribution d) {
    this.d = d;
  }

  public MicrofacetDistribution getMicrofacetDistribution() {
    return d;
  }

  @Override
  public void brdf(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f outDir, Color3f dest) {}

  @Override
  public float samplingReflectionDirection(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f dest) {
    return 1.0f;
  }

  @Override
  public float samplingTransmissionDirection(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f dest) {
    return 1.0f;
  }

}
