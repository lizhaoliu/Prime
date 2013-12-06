package prime.physics;

import prime.math.Vec3;

public class IdealSpecularModel extends BSDF {

    @Override
    public float samplingReflectionDirection(Vec3 origin, Vec3 normal,
	    Vec3 inDir, Vec3 dest) {
	// TODO Auto-generated method stub
	float cos = 2 * cosThetaAbsoluteValue(normal, inDir);
	dest.set(normal.x * cos + inDir.x, normal.y * cos + inDir.y, normal.z
		* cos + inDir.z);
	return 1.0f;
    }

    @Override
    public float samplingTransmissionDirection(Vec3 origin,
	    Vec3 normal, Vec3 inDir, Vec3 dest) {
	// TODO Auto-generated method stub
	return 1.0f;
    }

    @Override
    public void brdf(Vec3 origin, Vec3 normal, Vec3 inDir,
	    Vec3 outDir, Spectrum dest) {
	// TODO Auto-generated method stub
	Vec3 buf = Vec3.add(inDir, outDir);
	if (Math.abs(Vec3.dot(buf, normal)) > 0.01) {
	    dest.set(0, 0, 0);
	}

	buf = Vec3.cross(inDir, outDir);
	if (Math.abs(Vec3.dot(buf, normal)) > 0.01) {
	    dest.set(0, 0, 0);
	}

	dest.set(reflectance);
	dest.multiply(2.0f);
    }

    @Override
    public void btdf(Vec3 origin, Vec3 normal, Vec3 inDir,
	    Vec3 outDir, Spectrum dest) {
	// TODO Auto-generated method stub

    }

}
