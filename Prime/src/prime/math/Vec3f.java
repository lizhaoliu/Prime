package prime.math;

import java.io.Serializable;

/**
 * 3-dimension vector to represent a point or direction in space
 *
 * @author lizhaoliu
 */
public class Vec3f implements Serializable, Cloneable {
  private static final long serialVersionUID = 7314440075896007971L;

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
   * @param v
   */
  public void set(Vec3f v) {
    x = v.x;
    y = v.y;
    z = v.z;
  }

  /**
   * @param x
   * @param y
   * @param z
   * @return
   */
  public Vec3f set(float x, float y, float z) {
    this.x = x;
    this.y = y;
    this.z = z;
    return this;
  }

  /**
   * @param value
   * @return
   */
  public Vec3f set(float value) {
    this.x = value;
    this.y = value;
    this.z = value;
    return this;
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
    x *= invLen;
    y *= invLen;
    z *= invLen;
    return this;
  }

  /**
   * @param v
   * @return
   */
  public Vec3f add(Vec3f v) {
    x += v.x;
    y += v.y;
    z += v.z;
    return this;
  }

  /**
   * @param x
   * @param y
   * @param z
   * @return
   */
  public Vec3f add(float x, float y, float z) {
    this.x += x;
    this.y += y;
    this.z += z;
    return this;
  }

  /**
   * @param v
   * @return
   */
  public Vec3f sub(Vec3f v) {
    x -= v.x;
    y -= v.y;
    z -= v.z;
    return this;
  }

  /**
   * @param x
   * @param y
   * @param z
   * @return
   */
  public Vec3f sub(float x, float y, float z) {
    this.x -= x;
    this.y -= y;
    this.z -= z;
    return this;
  }

  /**
   * @param a
   * @return
   */
  public Vec3f mul(float a) {
    x *= a;
    y *= a;
    z *= a;
    return this;
  }

  /**
   * @return
   */
  public Vec3f negate() {
    x = -x;
    y = -y;
    z = -z;
    return this;
  }

  @Override
  public String toString() {
    return "Vec3f [x=" + x + ", y=" + y + ", z=" + z + "]";
  }
}
