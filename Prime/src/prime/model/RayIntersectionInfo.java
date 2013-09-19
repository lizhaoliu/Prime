package prime.model;

/**
 * 
 * @author lizhaoliu
 *
 */
public final class RayIntersectionInfo {
    private boolean isIntersected;
    private Triangle hitTriangle;
    private float u, v;

    public final void assign(RayIntersectionInfo conf) {
	isIntersected = conf.isIntersected;
	hitTriangle = conf.hitTriangle;
	u = conf.u;
	v = conf.v;
    }

    /**
     * 
     * @param flag
     */
    public final void setIsIntersected(boolean flag) {
	isIntersected = flag;
    }

    /**
     * 
     * @param t
     */
    public final void setHitTriangle(Triangle t) {
	hitTriangle = t;
    }

    /**
     * get the barycentric coordinates
     * 
     * @param w
     * @param u
     * @param v
     */
    public final void setUV(float u, float v) {
	this.u = u;
	this.v = v;
    }

    public final float getU() {
	return u;
    }

    public final float getV() {
	return v;
    }

    /**
     * 
     * @return
     */
    public final boolean isIntersected() {
	return isIntersected;
    }

    /**
     * 
     * @return
     */
    public final Triangle getTriangle() {
	return hitTriangle;
    }
}
