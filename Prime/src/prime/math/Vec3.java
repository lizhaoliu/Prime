package prime.math;

import java.io.Serializable;

/**
 * 3-dimension vector to represent a point or direction in space
 * 
 * @author lizhaoliu
 */
public class Vec3 implements Serializable, Cloneable {
	private static final long serialVersionUID = 7314440075896007971L;

	private static float[] xDir, yDir, zDir;

	static {
		xDir = new float[256];
		yDir = new float[256];
		zDir = new float[256];

		float theta;
		for (int i = 0; i < 256; i++) {
			theta = (float) (i / 256.0 * Math.PI);
			xDir[i] = (float) (Math.cos(theta) * Math.cos(theta));
		}
		for (int i = 0; i < 256; i++) {
			theta = (float) (i / 256.0 * Math.PI);
			yDir[i] = (float) (Math.cos(theta) * Math.sin(theta));
		}
		for (int i = 0; i < 256; i++) {
			theta = (float) (i / 256.0 * Math.PI);
			zDir[i] = (float) (Math.sin(theta));
		}
	}

	/**
	 * coordinate values
	 */
	public float x;
	public float y;
	public float z;

	/**
	 * 
	 */
	public Vec3() {
	}

	public Vec3(int code) {
		z = zDir[code & 0x000000ff];
		code >>= 8;
		y = zDir[code & 0x0000ff];
		code >>= 8;
		x = zDir[code & 0x00ff];
		normalize();
	}

	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 */
	public Vec3(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/**
	 * 
	 * @param v
	 */
	public Vec3(Vec3 v) {
		x = v.x;
		y = v.y;
		z = v.z;
	}

	/**
	 * 
	 * @return
	 */
	public float getLength() {
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	/**
	 * 
	 * @param i
	 * @return
	 */
	public float get(int i) {
		switch (i) {
		case 0:
			return x;
		case 1:
			return y;
		default:
			return z;
		}
	}

	/**
	 * 
	 * @param v
	 */
	public void set(Vec3 v) {
		x = v.x;
		y = v.y;
		z = v.z;
	}

	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public Vec3 set(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}

	/**
	 * 
	 * @param i
	 * @param val
	 * @return
	 */
	public Vec3 set(int i, float val) {
		switch (i) {
		case 0:
			x = val;
			return this;
		case 1:
			y = val;
			return this;
		default:
			z = val;
			return this;
		}
	}

	/**
	 * 
	 * @return
	 */
	public Vec3 normalize() {
		float invLen = 1.0f / (float) Math.sqrt(x * x + y * y + z * z);
		x *= invLen;
		y *= invLen;
		z *= invLen;
		return this;
	}

	/**
	 * 
	 * @param v
	 * @return
	 */
	public Vec3 add(Vec3 v) {
		x += v.x;
		y += v.y;
		z += v.z;
		return this;
	}

	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public Vec3 add(float x, float y, float z) {
		this.x += x;
		this.y += y;
		this.z += z;
		return this;
	}

	/**
	 * 
	 * @param v1
	 * @param v2
	 * @param dst
	 * @return
	 */
	public static Vec3 add(Vec3 v1, Vec3 v2) {
		return new Vec3(v1.x + v2.x, v1.y + v2.y, v1.z + v2.z);
	}

	/**
	 * 
	 * @param v
	 * @return
	 */
	public Vec3 sub(Vec3 v) {
		x -= v.x;
		y -= v.y;
		z -= v.z;
		return this;
	}

	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public Vec3 sub(float x, float y, float z) {
		this.x -= x;
		this.y -= y;
		this.z -= z;
		return this;
	}

	/**
	 * 
	 * @param v1
	 * @param v2
	 * @param dst
	 * @return
	 */
	public static Vec3 sub(Vec3 v1, Vec3 v2) {
		return new Vec3(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z);
	}

	/**
	 * 
	 * @param a
	 * @return
	 */
	public Vec3 mul(float a) {
		x *= a;
		y *= a;
		z *= a;
		return this;
	}

	/**
	 * 
	 * @param a
	 * @param v
	 * @param dst
	 * @return
	 */
	public static Vec3 mul(float a, Vec3 v) {
		return new Vec3(a * v.x, a * v.y, a * v.z);
	}

	/**
	 * 
	 * @param v
	 * @param a
	 * @param dst
	 * @return
	 */
	public static Vec3 mul(Vec3 v, float a) {
		return new Vec3(a * v.x, a * v.y, a * v.z);
	}

	/**
	 * 
	 * @param m
	 * @param v
	 * @param dst
	 * @return
	 */
	public static Vec3 mul(Mat3 m, Vec3 v) {
		return new Vec3 (m.m00 * v.x + m.m01 * v.y + m.m02 * v.z, 
				m.m10 * v.x + m.m11 * v.y + m.m12 * v.z, 
				m.m20 * v.x + m.m21 * v.y + m.m22 * v.z);
	}

	/**
	 * 
	 * @param v
	 * @param m
	 * @param dst
	 * @return
	 */
	public static Vec3 mul(Vec3 v, Mat3 m) {
		return new Vec3(m.m00 * v.x + m.m10 * v.y + m.m20 * v.z, m.m01 * v.x + m.m11
				* v.y + m.m21 * v.z, m.m02 * v.x + m.m12 * v.y + m.m22 * v.z);
	}

	/**
	 * 
	 * @param v1
	 * @param v2
	 * @param dst
	 * @return
	 */
	public static Vec3 cross(Vec3 v1, Vec3 v2) {
		return new Vec3(v1.y * v2.z - v1.z * v2.y, v1.z * v2.x - v1.x * v2.z,
				v1.x * v2.y - v1.y * v2.x);
	}

	/**
	 * 
	 * @return
	 */
	public Vec3 negate() {
		x = -x;
		y = -y;
		z = -z;
		return this;
	}

	/**
	 * 
	 * @param v1
	 * @param v2
	 * @return
	 */
	public static float dot(Vec3 v1, Vec3 v2) {
		return (v1.x * v2.x + v1.y * v2.y + v1.z * v2.z);
	}

	/**
	 * 
	 * @param v1
	 * @param v2
	 * @return
	 */
	public static float distance(Vec3 v1, Vec3 v2) {
		float dx = v1.x - v2.x, dy = v1.y - v2.y, dz = v1.z - v2.z;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * 
	 * @param v1
	 * @param v2
	 * @return
	 */
	public static float distanceSqr(Vec3 v1, Vec3 v2) {
		float dx = v1.x - v2.x, dy = v1.y - v2.y, dz = v1.z - v2.z;
		return (dx * dx + dy * dy + dz * dz);
	}

	/**
	 * 
	 * @param v1
	 * @param v2
	 * @param v3
	 * @return
	 */
	public static float tripleProduct(Vec3 v1, Vec3 v2, Vec3 v3) {
		return ((v1.y * v2.z - v1.z * v2.y) * v3.x
				+ (v1.z * v2.x - v1.x * v2.z) * v3.y + (v1.x * v2.y - v1.y
				* v2.x)
				* v3.z);
	}

	/**
	 * 
	 */
	public String toString() {
		return "x = " + x + ", y = " + y + ", z = " + z;
	}
}
