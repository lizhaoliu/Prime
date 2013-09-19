package prime.physics;

import prime.math.Vector;

/**
 * Bidirectional Scattering Distribution Function (BSDF) which describes a physical surface
 * @author lizhaoliu
 *
 */
public abstract class BSDF {
    protected Spectrum emittance = new Spectrum();
    protected Spectrum reflectance = new Spectrum(0.5f, 0.5f, 0.5f);
    protected Spectrum transmission = new Spectrum();
    protected Spectrum absorption = new Spectrum();
    protected float refractiveIndex = 1.0f;
    protected boolean isLight;
    protected String name;

    public static final float INV_PI = (float) (1.0f / Math.PI);
    public static final float INV_2PI = (float) (1.0f / (2 * Math.PI));

    public final void setEmittance(Spectrum emittance) {
	this.emittance.set(emittance);
    }

    public final Spectrum getEmittance() {
	return emittance;
    }

    public final void setReflectance(Spectrum reflectance) {
	this.reflectance.set(reflectance);
    }

    public final Spectrum getReflectance() {
	return reflectance;
    }

    public final void setTransmission(Spectrum transmission) {
	this.transmission.set(transmission);
    }

    public final Spectrum getTransmission() {
	return transmission;
    }

    public final void setAbsorption(Spectrum absorption) {
	this.absorption.set(absorption);
    }

    public final Spectrum getAbsorption() {
	return absorption;
    }

    public final void setRefractiveIndex(float refractiveIndex) {
	this.refractiveIndex = refractiveIndex;
    }

    public final float getRefractiveIndex() {
	return refractiveIndex;
    }

    public final void setIsLight(boolean isLight) {
	this.isLight = isLight;
    }

    public final boolean isLight() {
	return isLight;
    }

    public final void setName(String name) {
	this.name = name.toString();
    }

    public final String getName() {
	return name;
    }

    public final String toString() {
	return name;
    }

    /**
     * compute sin<n, d>
     * 
     * @param normal
     * @param dir
     * @return
     */
    protected final float sinTheta(Vector normal, Vector dir) {
	float cos = Vector.dot(normal, dir);
	return (float) (Math.sqrt(1 - cos * cos));
    }

    /**
     * compute cos<n, d>
     * 
     * @param normal
     * @param dir
     * @return
     */
    protected final float cosThetaAbsoluteValue(Vector normal, Vector dir) {
	return (float) (Math.abs(Vector.dot(normal, dir)));
    }

    /**
     * bidirectional reflectance function
     * 
     * @param origin
     * @param normal
     * @param inDir
     * @param outDir
     * @param dest
     */
    public abstract void brdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest);

    /**
     * 
     * @param origin
     * @param normal
     * @param inDir
     * @param outDir
     * @param dest
     */
    public abstract void btdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest);

    /**
     * importance sampling reflection direction according to BSDF
     * 
     * @param origin
     * @param normal
     * @param inDir
     * @param dest
     * @return the probability of sampling direction
     */
    public abstract float samplingReflectionDirection(Vector origin,
	    Vector normal, Vector inDir, Vector dest);

    /**
     * importance sampling transmission direction according to BSDF
     * 
     * @param origin
     * @param normal
     * @param inDir
     * @param dest
     * @return the probability of sampling direction
     */
    public abstract float samplingTransmissionDirection(Vector origin,
	    Vector normal, Vector inDir, Vector dest);
}
