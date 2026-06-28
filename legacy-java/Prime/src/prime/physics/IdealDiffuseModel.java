package prime.physics;

import prime.math.MathUtils;
import prime.math.Vec3f;

import static prime.math.MathUtils.PI_DOUBLE;
import static prime.math.MathUtils.PI_HALF;
import static prime.math.MathUtils.cos;
import static prime.math.MathUtils.rand;
import static prime.math.MathUtils.sin;

/**
 * @author lizhaoliu
 */
public class IdealDiffuseModel extends Material {

  @Override
  public Sample samplingReflectionDirection(Vec3f origin, Vec3f normal, Vec3f inDir) {
    float a = rand(0, PI_HALF), b = rand(0, PI_DOUBLE);
    float sina = sin(a), cosa = cos(a), sinb = sin(b), cosb = cos(b);
    Vec3f outDir = new Vec3f(cosa * cosb, cosa * sinb, sina);
    if (MathUtils.dot(inDir, outDir) > 0) {
      outDir = outDir.negate();
    }
    return new Sample(outDir, INV_2PI);
  }

  @Override
  public Sample samplingTransmissionDirection(Vec3f origin, Vec3f normal, Vec3f inDir) {
    // TODO implement this
    return new Sample(Vec3f.ZERO, 0);
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
