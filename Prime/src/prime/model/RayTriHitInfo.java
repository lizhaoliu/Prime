package prime.model;

import prime.physics.Ray;

/**
 * {@link Ray} and {@link Triangle} intersection test result
 */
public class RayTriHitInfo {
  private boolean isHit;
  private Triangle hitTriangle;
  private float u, v;

  public void assign(RayTriHitInfo conf) {
    isHit = conf.isHit;
    hitTriangle = conf.hitTriangle;
    u = conf.u;
    v = conf.v;
  }

  /**
   * 
   * @param flag
   */
  public void setIsIntersected(boolean flag) {
    isHit = flag;
  }

  /**
   * 
   * @param t
   */
  public void setHitTriangle(Triangle t) {
    hitTriangle = t;
  }

  /**
   * get the barycentric coordinates
   * 
   * @param w
   * @param u
   * @param v
   */
  public void setUV(float u, float v) {
    this.u = u;
    this.v = v;
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
  public Triangle getTriangle() {
    return hitTriangle;
  }
}
