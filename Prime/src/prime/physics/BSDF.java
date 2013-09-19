package prime.physics;

import prime.math.Vec3;

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

    public void setEmittance(Spectrum emittance) {
	this.emittance.set(emittance);
    }

    public Spectrum getEmittance() {
	return emittance;
    }

    public void setReflectance(Spectrum reflectance) {
	this.reflectance.set(reflectance);
    }

    public Spectrum getReflectance() {
	return reflectance;
    }

    public void setTransmission(Spectrum transmission) {
	this.transmission.set(transmission);
    }

    public Spectrum getTransmission() {
	return transmission;
    }

    public void setAbsorption(Spectrum absorption) {
	this.absorption.set(absorption);
    }

    public Spectrum getAbsorption() {
	return absorption;
    }

    public void setRefractiveIndex(float refractiveIndex) {
	this.refractiveIndex = refractiveIndex;
    }

    public float getRefractiveIndex() {
	return refractiveIndex;
    }

    public void setIsLight(boolean isLight) {
	this.isLight = isLight;
    }

    public boolean isLight() {
	return isLight;
    }

    public void setName(String name) {
	this.name = name.toString();
    }

    public String getName() {
	return name;
    }

    public String toString() {
	return name;
    }

    /**
     * compute sin<n, d>
     * 
     * @param normal
     * @param dir
     * @return
     */
    protected float sinTheta(Vec3 normal, Vec3 dir) {
	float cos = Vec3.dot(normal, dir);
	return (float) (Math.sqrt(1 - cos * cos));
    }

    /**
     * compute cos<n, d>
     * 
     * @param normal
     * @param dir
     * @return
     */
    protected float cosThetaAbsoluteValue(Vec3 normal, Vec3 dir) {
	return (float) (Math.abs(Vec3.dot(normal, dir)));
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
    public abstract void brdf(Vec3 origin, Vec3 normal, Vec3 inDir,
	    Vec3 outDir, Spectrum dest);

    /**
     * 
     * @param origin
     * @param normal
     * @param inDir
     * @param outDir
     * @param dest
     */
    public abstract void btdf(Vec3 origin, Vec3 normal, Vec3 inDir,
	    Vec3 outDir, Spectrum dest);

    /**
     * importance sampling reflection direction according to BSDF
     * 
     * @param origin
     * @param normal
     * @param inDir
     * @param dest
     * @return the probability of sampling direction
     */
    public abstract float samplingReflectionDirection(Vec3 origin,
	    Vec3 normal, Vec3 inDir, Vec3 dest);

    /**
     * importance sampling transmission direction according to BSDF
     * 
     * @param origin
     * @param normal
     * @param inDir
     * @param dest
     * @return the probability of sampling direction
     */
    public abstract float samplingTransmissionDirection(Vec3 origin,
	    Vec3 normal, Vec3 inDir, Vec3 dest);
}
