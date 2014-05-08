package prime.model;

import prime.physics.Ray;

/**
 * {@link Ray} and {@link Triangle} intersection test result
 */
public class RayTriHitInfo {
  private final boolean isHit;
  private final Triangle hitTriangle;
  private final float u;
  private final float v;

  public RayTriHitInfo() {
    this(false, null, 0, 0);
  }

  public RayTriHitInfo(boolean isHit, Triangle hitTriangle, float u, float v) {
    this.isHit = isHit;
    this.hitTriangle = hitTriangle;
    this.u = u;
    this.v = v;
  }

  public boolean isHit() {
    return isHit;
  }

  public Triangle getHitTriangle() {
    return hitTriangle;
  }

  public float getU() {
    return u;
  }

  public float getV() {
    return v;
  }
}
