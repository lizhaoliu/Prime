/**
 * 
 */
package prime.spatial;


import prime.model.BoundingBox;
import prime.model.RayIntersectionInfo;
import prime.physics.Ray;

/**
 * brutal-force structure
 * @author lizhaoliu
 *
 */
public class NaiveStructure extends SpatialStructure {
    public NaiveStructure(BoundingBox box) {
	super(box);
    }
    
    /**
     * 
     */
    @Override
    public void intersect(Ray ray, RayIntersectionInfo dst) {
	// TODO Auto-generated method stub
	box.intersect(ray, dst);
    }
}
