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
    public void translate(Vector displacement);

    /**
     * 
     * @param axis
     * @param angle
     */
    public void rotate(Vector axis, float angle);
}
