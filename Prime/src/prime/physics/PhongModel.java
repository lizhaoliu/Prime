package prime.physics;

import prime.math.Vec3;

/**
 * 
 * @author lizhaoliu
 *
 */
public class PhongModel extends BSDF {
    private float exponent = 20;

    public void setSpecularExponent(float exponent) {
	this.exponent = exponent;
    }

    @Override
    public void brdf(Vec3 origin, Vec3 normal, Vec3 inDir,
	    Vec3 outDir, Spectrum dest) {
	// TODO Auto-generated method stub
    }

    @Override
    public float samplingReflectionDirection(Vec3 origin,
	    Vec3 normal, Vec3 inDir, Vec3 dest) {
	// TODO Auto-generated method stub
	float zx = inDir.x, zy = inDir.y, zz = inDir.z;
	float xx = zy, xy = -zx, xz = 0;
	float invLen = 1.0f / (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
	xx *= invLen;
	xy *= invLen;
	float yx = xy * zz - xz * zy, yy = xz * zx - xx * zz, yz = xx * zy - xy
		* zx;
	double phy = 2 * Math.PI * Math.random();
	float costh = (float) Math.pow(1 - Math.random(), 1.0 / (exponent + 1)), sinth = (float) Math
		.sqrt(1 - costh * costh), cosphy = (float) Math.cos(phy), sinphy = (float) Math
		.sin(phy);
	float sincos = sinth * cosphy, sinsin = sinth * sinphy;
	dest.set(sincos * xx + sinsin * yx + costh * zx, sincos * xy + sinsin
		* yy + costh * zy, sincos * xz + sinsin * yz + costh * zz);
	return 0;
    }

    @Override
    public float samplingTransmissionDirection(Vec3 origin,
	    Vec3 normal, Vec3 inDir, Vec3 dest) {
	// TODO Auto-generated method stub
	return 0;
    }

    @Override
    public void btdf(Vec3 origin, Vec3 normal, Vec3 inDir,
	    Vec3 outDir, Spectrum dest) {
	// TODO Auto-generated method stub

    }

}
