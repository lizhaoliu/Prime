/**
 * 
 */
package prime.spatial;

import prime.model.BoundingBox;
import prime.model.RayTriIntInfo;
import prime.physics.Ray;

/**
 * provides an interface for scene-ray intersection
 * @author lizhaoliu
 *
 */
public abstract class SpatialStructure {
	protected BoundingBox box;

	public SpatialStructure(BoundingBox box) {
		this.box = box;
	}

	public abstract void intersect(Ray ray, RayTriIntInfo dst);
}
