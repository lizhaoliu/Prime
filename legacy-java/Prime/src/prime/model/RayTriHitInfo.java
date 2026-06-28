package prime.model;

import javax.annotation.Nonnull;

import prime.physics.Ray;

/**
 * {@link Ray} and {@link Triangle} intersection test result
 */
public class RayTriHitInfo {
  private final boolean isHit;
  private final Triangle hitTriangle;
  private final float u;
  private final float v;

  /**
   * Construct a default NOT hit result
   */
  public RayTriHitInfo() {
    this(false, null, 0, 0);
  }

  /**
   * 
   * @param isHit
   * @param hitTriangle
   * @param u
   * @param v
   */
  public RayTriHitInfo(boolean isHit, @Nonnull Triangle hitTriangle, float u, float v) {
    this.isHit = isHit;
    this.hitTriangle = hitTriangle;
    this.u = u;
    this.v = v;
  }

  /**
   * 
   * @return
   */
  public boolean isHit() {
    return isHit;
  }

  /**
   * 
   * @return
   */
  public Triangle getHitTriangle() {
    return hitTriangle;
  }

  /**
   * 
   * @return
   */
  public float getU() {
    return u;
  }

  /**
   * 
   * @return
   */
  public float getV() {
    return v;
  }
}
