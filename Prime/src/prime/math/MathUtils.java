package prime.math;

/**
 * provides some common math operations
 * @author lizhaoliu
 *
 */
public final class MathUtils {
	public static float EPSILON = 0.001f;
	public static float E = (float) Math.E;
	public static float PI = (float) Math.PI;
	public static float PI_OVER_2 = PI / 2;
	public static float PI_OVER_3 = PI / 3;
	public static float PI2 = 2.0f * (float) Math.PI;
	public static float INV_PI = 1.0f / (float) Math.PI;
	public static float INV_PI2 = 1.0f / (float) (Math.PI * 2.0);

	/**
	 * 
	 */
	private MathUtils() {
	}

	/**
	 * 
	 * @param angle
	 * @return
	 */
	public static float toRadians(float angle) {
		return (float) Math.PI * angle / 180;
	}

	/**
	 * 
	 * @param r
	 * @return
	 */
	public static float toAngle(float r) {
		return r * 180 / (float) Math.PI;
	}

	/**
	 * 
	 * @param srcDir
	 * @param n
	 * @param dest
	 */
	public static void randomScatteredDirection(Vec3 srcDir, int n,
			Vec3 dest) {
		float zx = srcDir.x, zy = srcDir.y, zz = srcDir.z;
		float xx = zy, xy = -zx, xz = 0;
		float invLen = 1.0f / (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
		xx *= invLen;
		xy *= invLen;
		float yx = xy * zz - xz * zy, yy = xz * zx - xx * zz, yz = xx * zy - xy
				* zx;
		double phy = 2 * Math.PI * Math.random();
		float costh = (float) Math.pow(1 - Math.random(), 1.0 / (n + 1)), sinth = (float) Math
				.sqrt(1 - costh * costh), cosphy = (float) Math.cos(phy), sinphy = (float) Math
				.sin(phy);
		float sincos = sinth * cosphy, sinsin = sinth * sinphy;
		dest.set(sincos * xx + sinsin * yx + costh * zx, sincos * xy + sinsin
				* yy + costh * zy, sincos * xz + sinsin * yz + costh * zz);
	}

	/**
	 * 
	 * @param srcDir
	 * @param n
	 * @param i
	 * @param j
	 * @param nSamples
	 * @param dest
	 */
	public static void stratifiedScatteredDirection(Vec3 srcDir, int n,
			int i, int j, int nSamples, Vec3 dest) {
		float zx = srcDir.x, zy = srcDir.y, zz = srcDir.z;
		float xx = zy, xy = -zx, xz = 0;
		float invLen = 1.0f / (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
		xx *= invLen;
		xy *= invLen;
		float yx = xy * zz - xz * zy, yy = xz * zx - xx * zz, yz = xx * zy - xy
				* zx;
		double phy = 2 * Math.PI * stratifiedRandom(j, n);
		float costh = (float) Math.pow(1 - stratifiedRandom(i, n),
				1.0 / (n + 1)), sinth = (float) Math.sqrt(1 - costh * costh), cosphy = (float) Math
				.cos(phy), sinphy = (float) Math.sin(phy);
		float sincos = sinth * cosphy, sinsin = sinth * sinphy;
		dest.set(sincos * xx + sinsin * yx + costh * zx, sincos * xy + sinsin
				* yy + costh * zy, sincos * xz + sinsin * yz + costh * zz);
	}

	/**
	 * 
	 * @param i
	 * @param n
	 * @return
	 */
	public static float stratifiedRandom(int i, int n) {
		return (float) (i + Math.random()) / n;
	}

	/**
	 * 
	 * @param srcDir
	 * @param i
	 * @param j
	 * @param n
	 * @param dest
	 */
	public static void stratiefiedRandomReflectDirection(Vec3 srcDir,
			int i, int j, int n, Vec3 dest) {
		float zx = srcDir.x, zy = srcDir.y, zz = srcDir.z;
		float xx = zy, xy = -zx, xz = 0;
		float invLen = 1.0f / (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
		xx *= invLen;
		xy *= invLen;
		float yx = xy * zz - xz * zy, yy = xz * zx - xx * zz, yz = xx * zy - xy
				* zx;
		double phy = 2 * Math.PI * stratifiedRandom(j, n);
		float costh = (float) Math.pow(1 - stratifiedRandom(i, n),
				1.0 / (n + 1)), sinth = (float) Math.sqrt(1 - costh * costh), cosphy = (float) Math
				.cos(phy), sinphy = (float) Math.sin(phy);
		float sincos = sinth * cosphy, sinsin = sinth * sinphy;
		dest.set(sincos * xx + sinsin * yx + costh * zx, sincos * xy + sinsin
				* yy + costh * zy, sincos * xz + sinsin * yz + costh * zz);
	}

	/**
	 * 
	 * @param normal
	 * @param i
	 * @param j
	 * @param n
	 * @param dest
	 */
	public static void stratifiedRandomDirectionInHemisphere(
			Vec3 normal, int i, int j, int n, Vec3 dest) {
		float zx = normal.x, zy = normal.y, zz = normal.z;
		float xx = zy, xy = -zx, xz = 0;
		float invLen = 1.0f / (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
		xx *= invLen;
		xy *= invLen;
		float yx = xy * zz - xz * zy, yy = xz * zx - xx * zz, yz = xx * zy - xy
				* zx;
		double phy = 2 * Math.PI * stratifiedRandom(j, n);
		float costh = (float) Math.sqrt(1 - stratifiedRandom(i, n)), sinth = (float) Math
				.sqrt(1 - costh * costh), cosphy = (float) Math.cos(phy), sinphy = (float) Math
				.sin(phy);
		float sincos = sinth * cosphy, sinsin = sinth * sinphy;
		dest.set(sincos * xx + sinsin * yx + costh * zx, sincos * xy + sinsin
				* yy + costh * zy, sincos * xz + sinsin * yz + costh * zz);
	}

	/**
	 * 
	 * @param normal
	 * @param dest
	 */
	public static void randomDirectionInHemisphere(Vec3 normal,
			Vec3 dest) {
		randomDirectionInSphere(dest);
		if (Vec3.dot(normal, dest) < 0) {
			dest.negate();
		}
	}

	/**
	 * 
	 * @param dest
	 */
	public static void randomDirectionInSphere(Vec3 dest) {
		// float phy = (float)(2 * Math.PI * Math.random());
		// float costh = 1 - 2 * (float)Math.random(), sinth =
		// (float)Math.sqrt(1 - costh * costh), cosphy = (float)Math.cos(phy),
		// sinphy = (float)Math.sin(phy);
		// dest.set(sinth * cosphy, sinth * sinphy, costh);
		float x = (float) (1 - 2 * Math.random()), y = (float) (1 - 2 * Math
				.random()), z = (float) (1 - 2 * Math.random());
		dest.set(x, y, z);
		dest.normalize();
	}

	/**
	 * 
	 * @param d
	 * @param n
	 * @param dest
	 */
	public static void reflectDirection(Vec3 d, Vec3 n, Vec3 dest) {
		float dot = -2 * Vec3.dot(n, d);
		dest.set(n.x * dot + d.x, n.y * dot + d.y, n.z * dot + d.z);
	}

	/**
	 * 
	 * @param d
	 * @param n
	 * @param refraD
	 * @param refraT
	 * @param dest
	 */
	public static void refractDirection(Vec3 d, Vec3 n, float refraD,
			float refraT, Vec3 dest) {
		float q = refraD / refraT;
		float cosD = -Vec3.dot(n, d), cosT = 1 - q * q * (1 - cosD * cosD);
		if (cosT < 0) {
			reflectDirection(d, n, dest);
			return;
		}
		cosT = (float) Math.sqrt(cosT);
		float coef;
		if (cosD > 0) {
			coef = q * cosD - cosT;
		} else {
			coef = q * cosD + cosT;
		}
		dest.set(q * d.x + coef * n.x, q * d.y + coef * n.y, q * d.z + coef
				* n.z);
	}
}
