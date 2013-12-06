package prime.physics;

import prime.math.Vec3f;

public class IdealSpecularModel extends Material {

    @Override
    public float samplingReflectionDirection(Vec3f origin, Vec3f normal,
	    Vec3f inDir, Vec3f dest) {
	// TODO Auto-generated method stub
	float cos = 2 * cosThetaAbsoluteValue(normal, inDir);
	dest.set(normal.x * cos + inDir.x, normal.y * cos + inDir.y, normal.z
		* cos + inDir.z);
	return 1.0f;
    }

    @Override
    public float samplingTransmissionDirection(Vec3f origin,
	    Vec3f normal, Vec3f inDir, Vec3f dest) {
	// TODO Auto-generated method stub
	return 1.0f;
    }

    @Override
    public void brdf(Vec3f origin, Vec3f normal, Vec3f inDir,
	    Vec3f outDir, Color3f dest) {
	// TODO Auto-generated method stub
	Vec3f buf = Vec3f.add(inDir, outDir);
	if (Math.abs(Vec3f.dot(buf, normal)) > 0.01) {
	    dest.set(0, 0, 0);
	}

	buf = Vec3f.cross(inDir, outDir);
	if (Math.abs(Vec3f.dot(buf, normal)) > 0.01) {
	    dest.set(0, 0, 0);
	}

	dest.set(reflectance);
	dest.multiply(2.0f);
    }

    @Override
    public void btdf(Vec3f origin, Vec3f normal, Vec3f inDir,
	    Vec3f outDir, Color3f dest) {
	// TODO Auto-generated method stub

    }

}
