/**
 * 
 */
package prime.spatial;

import prime.core.Scene;
import prime.model.BoundingBox;
import prime.model.RayTriHitInfo;
import prime.physics.Ray;

/**
 * 
 */
public abstract class SpatialStructure {
  protected BoundingBox box;

  /**
   * 
   * @param box
   */
  public SpatialStructure(BoundingBox box) {
    this.box = box;
  }

  /**
   * Perform {@link Ray} and {@link Scene} intersection test
   * 
   * @param ray
   * @param dst
   */
  public abstract void intersect(Ray ray, RayTriHitInfo dst);
}
