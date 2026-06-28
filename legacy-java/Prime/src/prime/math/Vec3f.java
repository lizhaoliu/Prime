package prime.math;

import java.io.Serializable;

/**
 * 3-dimension vector to represent a point or direction in space
 *
 * @author lizhaoliu
 */
public class Vec3f implements Serializable, Cloneable {
  private static final long serialVersionUID = 7314440075896007971L;

  public static final Vec3f ZERO = new Vec3f();
  public static final Vec3f UNIT_X = new Vec3f(1, 0, 0);
  public static final Vec3f UNIT_Y = new Vec3f(0, 1, 0);
  public static final Vec3f UNIT_Z = new Vec3f(0, 0, 1);

  /**
   * coordinate values
   */
  public float x;
  public float y;
  public float z;

  /**
   *
   */
  public Vec3f() {
  }

  /**
   * @param x
   * @param y
   * @param z
   */
  public Vec3f(float x, float y, float z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /**
   * @param v
   */
  public Vec3f(Vec3f v) {
    x = v.x;
    y = v.y;
    z = v.z;
  }

  /**
   * @return
   */
  public float length() {
    return (float) Math.sqrt(x * x + y * y + z * z);
  }

  /**
   * @param i
   * @return
   */
  public float get(int i) {
    switch (i) {
      case 0:
        return x;
      case 1:
        return y;
      default:
        return z;
    }
  }

  /**
   * @param i
   * @param val
   * @return
   */
  public Vec3f set(int i, float val) {
    switch (i) {
      case 0:
        x = val;
        return this;
      case 1:
        y = val;
        return this;
      default:
        z = val;
        return this;
    }
  }

  /**
   * @return
   */
  public Vec3f normalize() {
    float invLen = 1.0f / (float) Math.sqrt(x * x + y * y + z * z);
    return new Vec3f(x * invLen, y * invLen, z * invLen);
  }

  /**
   * @return
   */
  public Vec3f negate() {
    return new Vec3f(-x, -y, -z);
  }

  @Override
  public String toString() {
    return "Vec3f [x=" + x + ", y=" + y + ", z=" + z + "]";
  }
}
