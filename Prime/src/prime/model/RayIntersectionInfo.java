package prime.model;

/**
 * 
 * @author lizhaoliu
 *
 */
public class RayIntersectionInfo {
    private boolean isIntersected;
    private Triangle hitTriangle;
    private float u, v;

    public void assign(RayIntersectionInfo conf) {
	isIntersected = conf.isIntersected;
	hitTriangle = conf.hitTriangle;
	u = conf.u;
	v = conf.v;
    }

    /**
     * 
     * @param flag
     */
    public void setIsIntersected(boolean flag) {
	isIntersected = flag;
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

    public float getU() {
	return u;
    }

    public float getV() {
	return v;
    }

    /**
     * 
     * @return
     */
    public boolean isIntersected() {
	return isIntersected;
    }

    /**
     * 
     * @return
     */
    public Triangle getTriangle() {
	return hitTriangle;
    }
}
