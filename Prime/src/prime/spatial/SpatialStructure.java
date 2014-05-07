/**
 * 
 */
package prime.spatial;

import prime.core.Scene;
import prime.model.BoundingBox;
import prime.model.RayTriHitInfo;
import prime.model.Triangle;
import prime.physics.Ray;

/**
 * A spatial acceleration data structure that helps performing ray-scene intersection test
 */
public abstract class SpatialStructure {
  protected BoundingBox box;

  /**
   * Build a spatial data structure given a {@link BoundingBox} containing a set of {@link Triangle}
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
   */
  public abstract RayTriHitInfo intersect(Ray ray);
}
