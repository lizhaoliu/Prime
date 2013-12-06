package prime.physics;

import prime.math.Vec3f;

/**
 * 
 * @author lizhaoliu
 *
 */
public class IdealDiffuseModel extends Material {

    @Override
    public float samplingReflectionDirection(Vec3f origin,
	    Vec3f normal, Vec3f inDir, Vec3f dest) {
	// TODO Auto-generated method stub
	do {
	    dest.set(2 * (float) Math.random() - 1,
		    2 * (float) Math.random() - 1,
		    2 * (float) Math.random() - 1);
	} while (Vec3f.dot(dest, normal) < 0);
	dest.normalize();
	return INV_2PI;
    }

    @Override
    public float samplingTransmissionDirection(Vec3f origin,
	    Vec3f normal, Vec3f inDir, Vec3f dest) {
	// TODO Auto-generated method stub
	return 0;
    }

    @Override
    public void brdf(Vec3f origin, Vec3f normal, Vec3f inDir,
	    Vec3f outDir, Color3f dest) {
	// TODO Auto-generated method stub
	dest.set(reflectance);
	dest.multiply(INV_2PI);
    }

    @Override
    public void btdf(Vec3f origin, Vec3f normal, Vec3f inDir,
	    Vec3f outDir, Color3f dest) {
	// TODO Auto-generated method stub

    }

}
