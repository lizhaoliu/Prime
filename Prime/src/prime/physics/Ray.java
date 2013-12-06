package prime.physics;

import java.io.Serializable;

import prime.math.Vec3;

/**
 * 
 * @author lizhaoliu
 * 
 */
public final class Ray implements Serializable {
	private static final long serialVersionUID = -406076263164160604L;

	private Vec3 origin = new Vec3(); //
	private Vec3 direction = new Vec3(); //
	private Spectrum spectrum = new Spectrum(); //
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
	public final void setOrigin(Vec3 v) {
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
	public final void setDirection(Vec3 v) {
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
	public final void setSpectrum(Spectrum c) {
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
	public final Vec3 getOrigin() {
		return new Vec3(origin);
	}

	/**
	 * 
	 * @param dst
	 */
	public final Vec3 getDirection() {
		return new Vec3(direction);
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
	public final Spectrum getSpectrum() {
		return spectrum;
	}

	public final String toString() {
		return ("Origin: " + origin + ", Direction: " + direction);
	}
}
