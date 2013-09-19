package prime.physics;

import java.io.Serializable;

import prime.math.Vector;

/**
 * 
 * @author lizhaoliu
 *
 */
public final class Ray implements Serializable {
    private static final long serialVersionUID = -406076263164160604L;

    private Vector origin = new Vector(); //
    private Vector direction = new Vector(); //
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
    public final void setOrigin(Vector v) {
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
    public final void setDirection(Vector v) {
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
    public final void getOrigin(Vector dst) {
	dst.set(origin);
    }

    /**
     * 
     * @param dst
     */
    public final void getDirection(Vector dst) {
	dst.set(direction);
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
