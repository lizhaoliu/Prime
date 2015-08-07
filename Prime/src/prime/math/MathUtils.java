package prime.math;

import java.util.Random;

/**
 * provides some common math operations
 */
public final class MathUtils {
  public static float EPSILON = 1e-6f;
  public static float E = (float) Math.E;
  public static float PI = (float) Math.PI;
  public static float PI_HALF = PI / 2;
  public static float PI_THIRD = PI / 3;
  public static float PI_DOUBLE = 2.0f * (float) Math.PI;
  public static float PI_INV = 1.0f / (float) Math.PI;
  public static float PI_DOUBLE_INV = 1.0f / (float) (Math.PI * 2.0);

  private static final Random RANDOM = new Random();

  private MathUtils() {
  }

  /**
   * @param angle
   * @return
   */
  public static float toRadians(float angle) {
    return (float) Math.PI * angle / 180;
  }

  /**
   * @param r
   * @return
   */
  public static float toAngle(float r) {
    return r * 180 / (float) Math.PI;
  }

  /**
   * @param srcDir
   * @param n
   */
  public static Vec3f randomScatteredDirection(Vec3f srcDir, int n) {
    float zx = srcDir.x, zy = srcDir.y, zz = srcDir.z;
    float xx = zy, xy = -zx, xz = 0;
    float invLen = 1.0f / (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
    xx *= invLen;
    xy *= invLen;
    float yx = xy * zz - xz * zy, yy = xz * zx - xx * zz, yz = xx * zy - xy * zx;
    double phy = 2 * Math.PI * random();
    float costh = (float) Math.pow(1 - random(), 1.0 / (n + 1)), sinth = (float) Math.sqrt(1 - costh * costh), cosphy =
        (float) Math
            .cos(phy), sinphy = (float) Math.sin(phy);
    float sincos = sinth * cosphy, sinsin = sinth * sinphy;
    return new Vec3f(sincos * xx + sinsin * yx + costh * zx, sincos * xy + sinsin * yy + costh * zy, sincos * xz
        + sinsin * yz + costh * zz);
  }

  /**
   * @param srcDir
   * @param n
   * @param i
   * @param j
   * @param nSamples
   * @param dest
   */
  public static void stratifiedScatteredDirection(Vec3f srcDir, int n, int i, int j, int nSamples, Vec3f dest) {
    float zx = srcDir.x, zy = srcDir.y, zz = srcDir.z;
    float xx = zy, xy = -zx, xz = 0;
    float invLen = 1.0f / (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
    xx *= invLen;
    xy *= invLen;
    float yx = xy * zz - xz * zy, yy = xz * zx - xx * zz, yz = xx * zy - xy * zx;
    double phy = 2 * Math.PI * stratifiedRandom(j, n);
    float costh = (float) Math.pow(1 - stratifiedRandom(i, n), 1.0 / (n + 1)), sinth = (float) Math.sqrt(1 - costh
        * costh), cosphy = (float) Math.cos(phy), sinphy = (float) Math.sin(phy);
    float sincos = sinth * cosphy, sinsin = sinth * sinphy;
    dest.set(sincos * xx + sinsin * yx + costh * zx, sincos * xy + sinsin * yy + costh * zy, sincos * xz + sinsin * yz
        + costh * zz);
  }

  /**
   * @param i
   * @param n
   * @return
   */
  public static float stratifiedRandom(int i, int n) {
    return (float) (i + random()) / n;
  }

  /**
   * @param srcDir
   * @param i
   * @param j
   * @param n
   * @param dest
   */
  public static void stratiefiedRandomReflectDirection(Vec3f srcDir, int i, int j, int n, Vec3f dest) {
    float zx = srcDir.x, zy = srcDir.y, zz = srcDir.z;
    float xx = zy, xy = -zx, xz = 0;
    float invLen = 1.0f / (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
    xx *= invLen;
    xy *= invLen;
    float yx = xy * zz - xz * zy, yy = xz * zx - xx * zz, yz = xx * zy - xy * zx;
    double phy = 2 * Math.PI * stratifiedRandom(j, n);
    float costh = (float) Math.pow(1 - stratifiedRandom(i, n), 1.0 / (n + 1)), sinth = (float) Math.sqrt(1 - costh
        * costh), cosphy = (float) Math.cos(phy), sinphy = (float) Math.sin(phy);
    float sincos = sinth * cosphy, sinsin = sinth * sinphy;
    dest.set(sincos * xx + sinsin * yx + costh * zx, sincos * xy + sinsin * yy + costh * zy, sincos * xz + sinsin * yz
        + costh * zz);
  }

  /**
   * @param normal
   * @param i
   * @param j
   * @param n
   * @param dest
   */
  public static void stratifiedRandomDirectionInHemisphere(Vec3f normal, int i, int j, int n, Vec3f dest) {
    float zx = normal.x, zy = normal.y, zz = normal.z;
    float xx = zy, xy = -zx, xz = 0;
    float invLen = 1.0f / (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
    xx *= invLen;
    xy *= invLen;
    float yx = xy * zz - xz * zy, yy = xz * zx - xx * zz, yz = xx * zy - xy * zx;
    double phy = 2 * Math.PI * stratifiedRandom(j, n);
    float costh = (float) Math.sqrt(1 - stratifiedRandom(i, n)), sinth = (float) Math.sqrt(1 - costh * costh), cosphy =
        (float) Math
            .cos(phy), sinphy = (float) Math.sin(phy);
    float sincos = sinth * cosphy, sinsin = sinth * sinphy;
    dest.set(sincos * xx + sinsin * yx + costh * zx, sincos * xy + sinsin * yy + costh * zy, sincos * xz + sinsin * yz
        + costh * zz);
  }

  /**
   * @param normal
   */
  public static Vec3f randomDirectionInHemisphere(Vec3f normal) {
    Vec3f ret = randomDirectionInSphere();
    if (dot(normal, ret) < 0) {
      ret.negate();
    }
    return ret;
  }

  /**
   * @return
   */
  public static float random() {
    return RANDOM.nextFloat();
  }

  /**
   */
  public static Vec3f randomDirectionInSphere() {
    // float phy = (float)(2 * Math.PI * random());
    // float costh = 1 - 2 * (float)random(), sinth =
    // (float)Math.sqrt(1 - costh * costh), cosphy = (float)Math.cos(phy),
    // sinphy = (float)Math.sin(phy);
    // dest.set(sinth * cosphy, sinth * sinphy, costh);
    Vec3f ret = new Vec3f((float) (1 - 2 * random()), (float) (1 - 2 * random()), (float) (1 - 2 * random()));
    ret.normalize();
    return ret;
  }

  /**
   * @param d
   * @param n
   * @param dest
   */
  public static void reflectDirection(Vec3f d, Vec3f n, Vec3f dest) {
    float dot = -2 * dot(n, d);
    dest.set(n.x * dot + d.x, n.y * dot + d.y, n.z * dot + d.z);
  }

  /**
   * @param d
   * @param n
   * @param refraD
   * @param refraT
   * @param dest
   */
  public static void refractDirection(Vec3f d, Vec3f n, float refraD, float refraT, Vec3f dest) {
    float q = refraD / refraT;
    float cosD = -dot(n, d), cosT = 1 - q * q * (1 - cosD * cosD);
    if (cosT < 0) {
      reflectDirection(d, n, dest);
      return;
    }
    cosT = (float) Math.sqrt(cosT);
    float coef;
    if (cosD > 0) {
      coef = q * cosD - cosT;
    } else {
      coef = q * cosD + cosT;
    }
    dest.set(q * d.x + coef * n.x, q * d.y + coef * n.y, q * d.z + coef * n.z);
  }


  /**
   * @param v1
   * @param v2
   * @return
   */
  public static Vec3f add(Vec3f v1, Vec3f v2) {
    return new Vec3f(v1.x + v2.x, v1.y + v2.y, v1.z + v2.z);
  }


  /**
   * @param a
   * @param v
   * @return
   */
  public static Vec3f mul(float a, Vec3f v) {
    return new Vec3f(a * v.x, a * v.y, a * v.z);
  }

  /**
   * @param v
   * @param a
   * @return
   */
  public static Vec3f mul(Vec3f v, float a) {
    return new Vec3f(a * v.x, a * v.y, a * v.z);
  }

  /**
   * @param m
   * @param v
   * @return
   */
  public static Vec3f mul(Mat3 m, Vec3f v) {
    return new Vec3f(
        m.m00 * v.x + m.m01 * v.y + m.m02 * v.z,
        m.m10 * v.x + m.m11 * v.y + m.m12 * v.z,
        m.m20 * v.x + m.m21 * v.y + m.m22 * v.z
    );
  }

  /**
   * @param v
   * @param m
   * @return
   */
  public static Vec3f mul(Vec3f v, Mat3 m) {
    return new Vec3f(
        m.m00 * v.x + m.m10 * v.y + m.m20 * v.z,
        m.m01 * v.x + m.m11 * v.y + m.m21 * v.z,
        m.m02 * v.x + m.m12 * v.y + m.m22 * v.z
    );
  }

  /**
   * @param v1
   * @param v2
   * @return
   */
  public static Vec3f cross(Vec3f v1, Vec3f v2) {
    return new Vec3f(v1.y * v2.z - v1.z * v2.y, v1.z * v2.x - v1.x * v2.z, v1.x * v2.y - v1.y * v2.x);
  }


  /**
   * @param v1
   * @param v2
   * @return
   */
  public static float dot(Vec3f v1, Vec3f v2) {
    return (v1.x * v2.x + v1.y * v2.y + v1.z * v2.z);
  }

  /**
   * @param v1
   * @param v2
   * @return
   */
  public static float dist(Vec3f v1, Vec3f v2) {
    float dx = v1.x - v2.x, dy = v1.y - v2.y, dz = v1.z - v2.z;
    return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  /**
   * @param v1
   * @param v2
   * @return
   */
  public static float distSqr(Vec3f v1, Vec3f v2) {
    float dx = v1.x - v2.x, dy = v1.y - v2.y, dz = v1.z - v2.z;
    return (dx * dx + dy * dy + dz * dz);
  }

  /**
   * @param v1
   * @param v2
   * @param v3
   * @return
   */
  public static float tripleProduct(Vec3f v1, Vec3f v2, Vec3f v3) {
    return ((v1.y * v2.z - v1.z * v2.y) * v3.x + (v1.z * v2.x - v1.x * v2.z) * v3.y +
        (v1.x * v2.y - v1.y * v2.x) * v3.z);
  }

  /**
   * @param matLeft
   * @param matRight
   * @return
   */
  public static Mat3 mul(Mat3 matLeft, Mat3 matRight) {
    return new Mat3(matLeft.m00 * matRight.m00 + matLeft.m01 * matRight.m10 + matLeft.m02 * matRight.m20, matLeft.m00
        * matRight.m01 + matLeft.m01 * matRight.m11 + matLeft.m02 * matRight.m21, matLeft.m00 * matRight.m02
        + matLeft.m01 * matRight.m12 + matLeft.m02 * matRight.m22, matLeft.m10 * matRight.m00 + matLeft.m11
        * matRight.m10 + matLeft.m12 * matRight.m20, matLeft.m10 * matRight.m01 + matLeft.m11 * matRight.m11
        + matLeft.m12 * matRight.m21, matLeft.m10 * matRight.m02 + matLeft.m11 * matRight.m12 + matLeft.m12
        * matRight.m22, matLeft.m20 * matRight.m00 + matLeft.m21 * matRight.m10 + matLeft.m22 * matRight.m20,
        matLeft.m20 * matRight.m01 + matLeft.m21 * matRight.m11 + matLeft.m22 * matRight.m21, matLeft.m20
        * matRight.m02 + matLeft.m21 * matRight.m12 + matLeft.m22 * matRight.m22);
  }

  /**
   * @param v1
   * @param v2
   * @return
   */
  public static Vec3f sub(Vec3f v1, Vec3f v2) {
    return new Vec3f(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z);
  }
}
