package prime.physics;

import prime.math.Vector;

public final class IdealSpecularModel extends BSDF {

    @Override
    public float samplingReflectionDirection(Vector origin, Vector normal,
	    Vector inDir, Vector dest) {
	// TODO Auto-generated method stub
	float cos = 2 * cosThetaAbsoluteValue(normal, inDir);
	dest.set(normal.x * cos + inDir.x, normal.y * cos + inDir.y, normal.z
		* cos + inDir.z);
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
	Vector buf = new Vector();

	Vector.add(inDir, outDir, buf);
	if (Math.abs(Vector.dot(buf, normal)) > 0.01) {
	    dest.set(0, 0, 0);
	}

	Vector.cross(inDir, outDir, buf);
	if (Math.abs(Vector.dot(buf, normal)) > 0.01) {
	    dest.set(0, 0, 0);
	}

	dest.set(reflectance);
	dest.multiply(2.0f);
    }

    @Override
    public final void btdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest) {
	// TODO Auto-generated method stub

    }

}
