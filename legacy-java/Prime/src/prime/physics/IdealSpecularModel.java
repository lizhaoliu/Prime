package prime.physics;

import prime.math.Vec3f;

import static prime.math.MathUtils.add;
import static prime.math.MathUtils.cross;
import static prime.math.MathUtils.dot;

public class IdealSpecularModel extends Material {

  @Override
  public Sample samplingReflectionDirection(Vec3f origin, Vec3f normal, Vec3f inDir) {
    float cos = 2 * cosThetaAbsoluteValue(normal, inDir);
    Vec3f outDir = new Vec3f(normal.x * cos + inDir.x, normal.y * cos + inDir.y, normal.z * cos + inDir.z);
    return new Sample(outDir, 1.0f);
  }

  @Override
  public Sample samplingTransmissionDirection(Vec3f origin, Vec3f normal, Vec3f inDir) {
    // TODO implement this
    return new Sample(Vec3f.ZERO, 0);
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
