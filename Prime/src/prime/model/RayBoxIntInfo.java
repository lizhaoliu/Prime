package prime.model;

import javax.annotation.concurrent.Immutable;

import prime.physics.Ray;

/**
 * {@link Ray} and {@link BoundingBox} intersection test result
 */
@Immutable
public class RayBoxIntInfo {
  private final boolean isHit;
  private final float near;
  private final float far;

  /**
   * If is hit
   * 
   * @return
   */
  public boolean isHit() {
    return isHit;
  }

  /**
   * Get the near hit distance
   * 
   * @return
   */
  public float getNear() {
    return near;
  }

  /**
   * Get the far hit distance
   * 
   * @return
   */
  public float getFar() {
    return far;
  }

  /**
   * 
   * 
   * @param isHit
   * @param near
   * @param far
   */
  public RayBoxIntInfo(boolean isHit, float near, float far) {
    this.isHit = isHit;
    this.near = near;
    this.far = far;
  }
}
