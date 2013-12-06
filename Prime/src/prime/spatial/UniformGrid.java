/**
 * 
 */
package prime.spatial;


import prime.model.BoundingBox;
import prime.model.RayTriIntInfo;
import prime.physics.Ray;

/**
 * 
 */
public class UniformGrid extends SpatialStructure {
    private int xDiv, yDiv, zDiv;
    
    private BoundingBox[][][] grids;
    
    public UniformGrid(BoundingBox box, int xDiv, int yDiv, int zDiv) {
	super(box);
	this.xDiv = xDiv;
	this.yDiv = yDiv;
	this.zDiv = zDiv;
    }
    
    public void subdivide() {
	grids = new BoundingBox[xDiv][yDiv][zDiv];
	for (int i = 0; i < xDiv; i++) {
	    for (int j = 0; j < yDiv; j++) {
		for (int k = 0; k < zDiv; k++) {
		    grids[i][j][k] = new BoundingBox();
		}
	    }
	}
    }
    
    public void setXDivision(int xDiv) {
	this.xDiv = xDiv;
    }
    
    public int getXDivision() {
	return xDiv;
    }
    
    public void setYDivision(int yDiv) {
	this.yDiv = yDiv;
    }
    
    public int getYDivision() {
	return yDiv;
    }
    
    public void setZDivision(int zDiv) {
	this.zDiv = zDiv;
    }
    
    public int getZDivision() {
	return zDiv;
    }
    
    /**
     * 
     */
    @Override
    public void intersect(Ray ray, RayTriIntInfo dst) {
	// TODO Auto-generated method stub

    }

}
