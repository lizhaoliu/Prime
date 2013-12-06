package prime.physics;

import java.io.Serializable;

import prime.math.Vec3f;

/**
 * 
 * @author lizhaoliu
 * 
 */
public final class Ray implements Serializable {
	private static final long serialVersionUID = -406076263164160604L;

	private Vec3f origin = new Vec3f(); //
	private Vec3f direction = new Vec3f(); //
	private Color3f spectrum = new Color3f(); //
	private float length = Float.MAX_VALUE; //
	private float n = 1.0f; //

	/**
     * 
     */
	public Ray() {
	}

	/**
	 * 
	 * @param v
	 */
	public final void setOrigin(Vec3f v) {
		origin.set(v);
	}

	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 */
	public final void setOrigin(float x, float y, float z) {
		origin.set(x, y, z);
	}

	/**
	 * 
	 * @param v
	 */
	public final void setDirection(Vec3f v) {
		direction.set(v);
		direction.normalize();
	}

	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 */
	public final void setDirection(float x, float y, float z) {
		direction.set(x, y, z);
	}

	/**
	 * 
	 * @param l
	 */
	public final void setLength(float l) {
		length = l;
	}

	/**
     * 
     */
	public final void setLengthToMax() {
		length = Float.MAX_VALUE;
	}

	/**
	 * 
	 * @param c
	 */
	public final void setSpectrum(Color3f c) {
		spectrum.set(c);
	}

	/**
	 * 
	 * @param refrac
	 */
	public final void setRefrativeIndex(float refrac) {
		n = refrac;
	}

	/**
	 * 
	 * @param dst
	 */
	public final Vec3f getOrigin() {
		return new Vec3f(origin);
	}

	/**
	 * 
	 * @param dst
	 */
	public final Vec3f getDirection() {
		return new Vec3f(direction);
	}

	/**
	 * 
	 * @return
	 */
	public final float getLength() {
		return length;
	}

	/**
	 * 
	 * @return
	 */
	public final float getRefractiveIndex() {
		return n;
	}

	/**
	 * 
	 * @return
	 */
	public final Color3f getSpectrum() {
		return spectrum;
	}

	public final String toString() {
		return ("Origin: " + origin + ", Direction: " + direction);
	}
}
