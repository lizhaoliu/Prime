package prime.physics;

import prime.math.Vec3f;

import static prime.math.MathUtils.add;
import static prime.math.MathUtils.cross;
import static prime.math.MathUtils.dot;

public class IdealSpecularModel extends Material {

  @Override
  public float samplingReflectionDirection(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f dest) {
    float cos = 2 * cosThetaAbsoluteValue(normal, inDir);
    dest.set(normal.x * cos + inDir.x, normal.y * cos + inDir.y, normal.z * cos + inDir.z);
    return 1.0f;
  }

  @Override
  public float samplingTransmissionDirection(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f dest) {
    return 1.0f;
  }

  @Override
  public void brdf(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f outDir, Color3f dest) {
    Vec3f buf = add(inDir, outDir);
    if (Math.abs(dot(buf, normal)) > 0.01) {
      dest.set(0, 0, 0);
    }

    buf = cross(inDir, outDir);
    if (Math.abs(dot(buf, normal)) > 0.01) {
      dest.set(0, 0, 0);
    }

    dest.set(reflectance);
    dest.multiply(2.0f);
  }

  @Override
  public void btdf(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f outDir, Color3f dest) {
  }

}
