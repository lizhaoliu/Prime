package prime.physics;

import prime.math.Vec3f;

/**
 * Bidirectional Scattering Distribution Function (BSDF) which describes a physical surface
 * 
 * @author lizhaoliu
 * 
 */
public abstract class Material {
  protected Color3f emittance = new Color3f();
  protected Color3f reflectance = new Color3f(0.5f, 0.5f, 0.5f);
  protected Color3f transmission = new Color3f();
  protected Color3f absorption = new Color3f();
  protected float refractiveIndex = 1.0f;
  protected boolean isLight;
  protected String name;

  public static final float INV_PI = (float) (1.0f / Math.PI);
  public static final float INV_2PI = (float) (1.0f / (2 * Math.PI));

  public void setEmittance(Color3f emittance) {
    this.emittance.set(emittance);
  }

  public Color3f getEmittance() {
    return emittance;
  }

  public void setReflectance(Color3f reflectance) {
    this.reflectance.set(reflectance);
  }

  public Color3f getReflectance() {
    return reflectance;
  }

  public void setTransmission(Color3f transmission) {
    this.transmission.set(transmission);
  }

  public Color3f getTransmission() {
    return transmission;
  }

  public void setAbsorption(Color3f absorption) {
    this.absorption.set(absorption);
  }

  public Color3f getAbsorption() {
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
  protected float sinTheta(Vec3f normal, Vec3f dir) {
    float cos = Vec3f.dot(normal, dir);
    return (float) (Math.sqrt(1 - cos * cos));
  }

  /**
   * compute cos<n, d>
   * 
   * @param normal
   * @param dir
   * @return
   */
  protected float cosThetaAbsoluteValue(Vec3f normal, Vec3f dir) {
    return (float) (Math.abs(Vec3f.dot(normal, dir)));
  }

  /**
   * Bidirectional reflectance function
   * 
   * @param origin
   * @param normal
   * @param inDir
   * @param outDir
   * @param dest
   */
  public abstract void brdf(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f outDir, Color3f dest);

  /**
   * 
   * @param origin
   * @param normal
   * @param inDir
   * @param outDir
   * @param dest
   */
  public abstract void btdf(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f outDir, Color3f dest);

  /**
   * Importance sampling reflection direction according to {@link Material}
   * 
   * @param origin
   * @param normal
   * @param inDir
   * @param dest
   * @return the probability of sampling direction
   */
  public abstract float samplingReflectionDirection(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f dest);

  /**
   * Importance sampling transmission direction according to {@link Material}
   * 
   * @param origin
   * @param normal
   * @param inDir
   * @param dest
   * @return the probability of sampling direction
   */
  public abstract float samplingTransmissionDirection(Vec3f origin, Vec3f normal, Vec3f inDir, Vec3f dest);
}
