package prime.physics;

import prime.math.Vec3f;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.Serializable;

/**
 * @author lizhaoliu
 */
@NotThreadSafe
public final class Ray implements Serializable {
  private static final long serialVersionUID = -406076263164160604L;

  private Vec3f origin = new Vec3f(); //
  private Vec3f direction = new Vec3f(); //
  private Color3f color = new Color3f(); //
  private float length = Float.MAX_VALUE; //
  private float n = 1.0f; //

  /**
   *
   */
  public Ray() {
  }

  /**
   * @param v
   */
  public final void setOrigin(Vec3f v) {
    origin.set(v);
  }

  /**
   * @param x
   * @param y
   * @param z
   */
  public final void setOrigin(float x, float y, float z) {
    origin.set(x, y, z);
  }

  /**
   * @param v
   */
  public final void setDirection(Vec3f v) {
    direction.set(v);
  }

  /**
   * @param x
   * @param y
   * @param z
   */
  public final void setDirection(float x, float y, float z) {
    direction.set(x, y, z);
  }

  /**
   * @param l
   */
  public final void setLength(float l) {
    length = l;
  }

  /**
   *
   */
  public final void setLengthToMax() {
    length = Float.MAX_VALUE;
  }

  /**
   * @param c
   */
  public final void setSpectrum(Color3f c) {
    color.set(c);
  }

  /**
   * @param refrac
   */
  public final void setRefrativeIndex(float refrac) {
    n = refrac;
  }

  /**
   *
   */
  public final Vec3f getOrigin() {
    return new Vec3f(origin);
  }

  /**
   *
   */
  public final Vec3f getDirection() {
    return new Vec3f(direction);
  }

  /**
   * @return
   */
  public final float getLength() {
    return length;
  }

  /**
   * @return
   */
  public final float getRefractiveIndex() {
    return n;
  }

  /**
   * @return
   */
  public final Color3f getColor() {
    return color;
  }

  @Override
  public String toString() {
    return "Ray [origin=" + origin + ", direction=" + direction + ", color=" + color + "]";
  }
}
