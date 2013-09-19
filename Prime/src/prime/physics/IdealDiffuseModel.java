package prime.physics;

import prime.math.Vec3;

/**
 * 
 * @author lizhaoliu
 *
 */
public class IdealDiffuseModel extends BSDF {

    @Override
    public float samplingReflectionDirection(Vec3 origin,
	    Vec3 normal, Vec3 inDir, Vec3 dest) {
	// TODO Auto-generated method stub
	do {
	    dest.set(2 * (float) Math.random() - 1,
		    2 * (float) Math.random() - 1,
		    2 * (float) Math.random() - 1);
	} while (Vec3.dot(dest, normal) < 0);
	dest.normalize();
	return INV_2PI;
    }

    @Override
    public float samplingTransmissionDirection(Vec3 origin,
	    Vec3 normal, Vec3 inDir, Vec3 dest) {
	// TODO Auto-generated method stub
	return 0;
    }

    @Override
    public void brdf(Vec3 origin, Vec3 normal, Vec3 inDir,
	    Vec3 outDir, Spectrum dest) {
	// TODO Auto-generated method stub
	dest.set(reflectance);
	dest.multiply(INV_2PI);
    }

    @Override
    public void btdf(Vec3 origin, Vec3 normal, Vec3 inDir,
	    Vec3 outDir, Spectrum dest) {
	// TODO Auto-generated method stub

    }

}
