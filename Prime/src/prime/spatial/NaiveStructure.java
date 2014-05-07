/**
 * 
 */
package prime.spatial;

import prime.model.BoundingBox;
import prime.model.RayTriHitInfo;
import prime.physics.Ray;

/**
 * brutal-force structure
 * 
 * @author lizhaoliu
 * 
 */
public class NaiveStructure extends SpatialStructure {
  public NaiveStructure(BoundingBox box) {
    super(box);
  }

  @Override
  public RayTriHitInfo intersect(Ray ray) {
    return box.intersectRayWithTriangles(ray);
  }
}
