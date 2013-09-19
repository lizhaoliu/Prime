package prime.math;

import java.io.Serializable;

/**
 * 3-dimension vector to represent a point or direction in space
 * 
 * @author Alan Liu
 */
public final class Vector implements Serializable, Cloneable {
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
	public Vector() {
	}

	public Vector(int code) {
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
	public Vector(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/**
	 * 
	 * @param v
	 */
	public Vector(Vector v) {
		x = v.x;
		y = v.y;
		z = v.z;
	}

	/**
	 * 
	 * @return
	 */
	public final float getLength() {
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	/**
	 * 
	 * @param i
	 * @return
	 */
	public final float get(int i) {
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
	public final void set(Vector v) {
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
	public final Vector set(float x, float y, float z) {
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
	public final Vector set(int i, float val) {
		switch (i) {
		case 0:
			x = val;
			break;
		case 1:
			y = val;
			break;
		default:
			z = val;
			break;
		}
		return this;
	}

	/**
	 * 
	 * @return
	 */
	public final Vector normalize() {
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
	public final Vector add(Vector v) {
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
	public final Vector add(float x, float y, float z) {
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
	public static final Vector add(Vector v1, Vector v2, Vector dst) {
		return dst.set(v1.x + v2.x, v1.y + v2.y, v1.z + v2.z);
	}

	/**
	 * 
	 * @param v
	 * @return
	 */
	public final Vector sub(Vector v) {
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
	public final Vector sub(float x, float y, float z) {
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
	public static final Vector sub(Vector v1, Vector v2, Vector dst) {
		dst.set(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z);
		return dst;
	}

	/**
	 * 
	 * @param a
	 * @return
	 */
	public final Vector mul(float a) {
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
	public static final Vector mul(float a, Vector v, Vector dst) {
		dst.set(a * v.x, a * v.y, a * v.z);
		return dst;
	}

	/**
	 * 
	 * @param v
	 * @param a
	 * @param dst
	 * @return
	 */
	public static final Vector mul(Vector v, float a, Vector dst) {
		dst.set(a * v.x, a * v.y, a * v.z);
		return dst;
	}

	/**
	 * 
	 * @param m
	 * @param v
	 * @param dst
	 * @return
	 */
	public static final Vector mul(Matrix m, Vector v, Vector dst) {
		dst.set(m.m00 * v.x + m.m01 * v.y + m.m02 * v.z, m.m10 * v.x + m.m11
				* v.y + m.m12 * v.z, m.m20 * v.x + m.m21 * v.y + m.m22 * v.z);
		return dst;
	}

	/**
	 * 
	 * @param v
	 * @param m
	 * @param dst
	 * @return
	 */
	public static final Vector mul(Vector v, Matrix m, Vector dst) {
		dst.set(m.m00 * v.x + m.m10 * v.y + m.m20 * v.z, m.m01 * v.x + m.m11
				* v.y + m.m21 * v.z, m.m02 * v.x + m.m12 * v.y + m.m22 * v.z);
		return dst;
	}

	/**
	 * 
	 * @param v1
	 * @param v2
	 * @param dst
	 * @return
	 */
	public static final Vector cross(Vector v1, Vector v2, Vector dst) {
		return dst.set(v1.y * v2.z - v1.z * v2.y, v1.z * v2.x - v1.x * v2.z,
				v1.x * v2.y - v1.y * v2.x);
	}

	/**
	 * 
	 * @return
	 */
	public final Vector negate() {
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
	public static final float dot(Vector v1, Vector v2) {
		return (v1.x * v2.x + v1.y * v2.y + v1.z * v2.z);
	}

	/**
	 * 
	 * @param v1
	 * @param v2
	 * @return
	 */
	public static final float distance(Vector v1, Vector v2) {
		float dx = v1.x - v2.x, dy = v1.y - v2.y, dz = v1.z - v2.z;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * 
	 * @param v1
	 * @param v2
	 * @return
	 */
	public static final float distanceSqr(Vector v1, Vector v2) {
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
	public static final float mixproduction(Vector v1, Vector v2, Vector v3) {
		return ((v1.y * v2.z - v1.z * v2.y) * v3.x
				+ (v1.z * v2.x - v1.x * v2.z) * v3.y + (v1.x * v2.y - v1.y
				* v2.x)
				* v3.z);
	}

	/**
	 * 
	 */
	public final String toString() {
		return "x = " + x + ", y = " + y + ", z = " + z;
	}
}
