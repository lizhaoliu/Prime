package prime.physics;

import prime.math.Vector;

/**
 * 
 * @author lizhaoliu
 *
 */
public final class PhongModel extends BSDF {
    private float exponent = 20;

    public final void setSpecularExponent(float exponent) {
	this.exponent = exponent;
    }

    @Override
    public final void brdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest) {
	// TODO Auto-generated method stub
    }

    @Override
    public final float samplingReflectionDirection(Vector origin,
	    Vector normal, Vector inDir, Vector dest) {
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
    public final float samplingTransmissionDirection(Vector origin,
	    Vector normal, Vector inDir, Vector dest) {
	// TODO Auto-generated method stub
	return 0;
    }

    @Override
    public final void btdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest) {
	// TODO Auto-generated method stub

    }

}
