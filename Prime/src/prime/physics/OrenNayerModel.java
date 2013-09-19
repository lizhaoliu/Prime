package prime.physics;

import prime.math.Vector;

public final class OrenNayerModel extends BSDF {
    private float sigma = 50; // gaussian distribution parameter

    public final void setSigma(float sigma) {
	this.sigma = sigma;
    }

    public final float getSigma() {
	return sigma;
    }

    @Override
    public final void brdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest) {
	// TODO Auto-generated method stub
	float sigma2 = sigma * sigma;
	float A = 1 - sigma2 / (2 * (sigma2 + 0.33f));
	float B = 0.45f * sigma2 / (sigma2 + 0.09f);
	float cosi = cosThetaAbsoluteValue(normal, inDir), sini = sinTheta(
		normal, inDir);
	float coso = cosThetaAbsoluteValue(normal, outDir), sino = sinTheta(
		normal, outDir);
	float sinalpha, tanbeta;
	if (cosi > coso) {
	    sinalpha = sino;
	    tanbeta = sini / cosi;
	} else {
	    sinalpha = sini;
	    tanbeta = sino / coso;
	}
	float factor = INV_PI
		* (A + B * Math.max(0, cosi * coso + sini * sino) * sinalpha
			* tanbeta);
	dest.set(reflectance);
	dest.multiply(factor);
    }

    @Override
    public final float samplingReflectionDirection(Vector origin,
	    Vector normal, Vector inDir, Vector dest) {
	// TODO Auto-generated method stub
	do {
	    dest.set(2 * (float) Math.random() - 1,
		    2 * (float) Math.random() - 1,
		    2 * (float) Math.random() - 1);
	} while (Vector.dot(dest, normal) < 0);
	dest.normalize();
	return INV_2PI;
    }

    @Override
    public final float samplingTransmissionDirection(Vector origin,
	    Vector normal, Vector inDir, Vector dest) {
	// TODO Auto-generated method stub
	return 0.0f;
    }

    @Override
    public final void btdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest) {
	// TODO Auto-generated method stub

    }

}
