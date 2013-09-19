package prime.physics;

import prime.math.Vector;

/**
 * 
 * @author lizhaoliu
 *
 */
public final class IdealDiffuseModel extends BSDF {

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
	return 0;
    }

    @Override
    public final void brdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest) {
	// TODO Auto-generated method stub
	dest.set(reflectance);
	dest.multiply(INV_2PI);
    }

    @Override
    public final void btdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest) {
	// TODO Auto-generated method stub

    }

}
