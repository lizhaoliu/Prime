package prime.physics;

import prime.math.Vec3f;

import static prime.math.MathUtils.dot;

/**
 * @author lizhaoliu
 */
public class IdealDiffuseModel extends Material {

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
    return 0;
  }

  @Override
  public void brdf(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f outDir, Color3f dest) {
    dest.set(reflectance);
    dest.multiply(INV_2PI);
  }

  @Override
  public void btdf(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f outDir, Color3f dest) {
  }
}
