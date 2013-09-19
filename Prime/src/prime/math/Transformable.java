package prime.math;

/**
 * 
 * @author lizhaoliu
 * 
 */
public interface Transformable {
    /**
     * 
     * @param displacement
     */
    public void translate(Vec3 displacement);

    /**
     * 
     * @param axis
     * @param angle
     */
    public void rotate(Vec3 axis, float angle);
}
