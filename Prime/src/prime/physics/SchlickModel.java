package prime.physics;

import prime.math.Vector;

public final class SchlickModel extends BSDF {
    private float f0; // specular reflection at normal incidence
    private float sigma; // roughness factor(0 : perfectly smooth; 1 : ideal
			 // diffuse
    private float psi; // isotropy factor(0 : perfect anisotropic; 1 : isotropic

    @Override
    public final float samplingReflectionDirection(Vector origin,
	    Vector normal, Vector inDir, Vector dest) {
	// TODO Auto-generated method stub
	return 1.0f;
    }

    @Override
    public final float samplingTransmissionDirection(Vector origin,
	    Vector normal, Vector inDir, Vector dest) {
	// TODO Auto-generated method stub
	return 1.0f;
    }

    @Override
    public final void brdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest) {
	// TODO Auto-generated method stub
	Vector H = new Vector();
	Vector.add(inDir, outDir, H);
	H.normalize();
	float u = Vector.dot(outDir, H);
	float t = Vector.dot(normal, H);
	float v = Vector.dot(outDir, normal);
	float vdot = -Vector.dot(inDir, normal);
	float w = (float) (Math.random());
	float g = 4 * sigma * (1 - sigma);
	float d = (sigma < 0.5f ? 0.0f : 1 - g);
	float s = (sigma < 0.5f ? 1 - g : 0.0f);

	float factor = (float) (S(u) * (d / Math.PI + g * D(t, v, vdot, w) + s));
	dest.set(factor, factor, factor);
    }

    private final float S(float u) {
	return f0 + (float) ((1 - f0) * Math.pow(1 - u, 5));
    }

    private final float Z(float t) {
	float div = (1 - sigma * t - t * t);
	return sigma / (div * div);
    }

    private final float D(float t, float v, float vdot, float w) {
	return (float) ((G(v) * G(vdot) * Z(t) * A(w) + 1 - G(v) * G(vdot)) / (4
		* Math.PI * v * vdot));
    }

    private final float A(float w) {
	return (float) (Math.sqrt(psi / (psi * psi * (1 - w * w) + w * w)));
    }

    private final float G(float v) {
	return (v / (sigma - sigma * v + v));
    }

    @Override
    public void btdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest) {
	// TODO Auto-generated method stub

    }
}
