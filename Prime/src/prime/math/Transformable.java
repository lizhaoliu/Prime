package prime.math;

/**
 * 
 */
public interface Transformable {
	
    /**
     * 
     * @param displacement
     */
    public void translate(Vec3f displacement);

    /**
     * Rotate angle around axis
     * @param axis
     * @param angle
     */
    public void rotate(Vec3f axis, float angle);
}
