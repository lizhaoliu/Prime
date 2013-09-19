package prime.physics;

import java.io.Serializable;

/**
 * 
 * @author lizhaoliu
 *
 */
public final class Spectrum implements Serializable {
	private static final long serialVersionUID = -7257336123202843467L;
	public float r = 0f, g = 0f, b = 0f;

	public Spectrum() {
	}

	public Spectrum(float r, float g, float b) {
		this.r = r;
		this.g = g;
		this.b = b;
	}

	public Spectrum(Spectrum c) {
		r = c.r;
		g = c.g;
		b = c.b;
	}

	/**
	 * 
	 * @param argb
	 */
	public Spectrum(int argb) {
		b = (argb & 0x000000ff) / 255.0f;
		argb >>= 8;
		g = (argb & 0x0000ff) / 255.0f;
		argb >>= 8;
		r = (argb & 0x00ff) / 255.0f;
	}

	/**
	 * 
	 * @param c
	 */
	public final void set(Spectrum c) {
		r = c.r;
		g = c.g;
		b = c.b;
	}

	/**
	 * 
	 * @param ar
	 * @param ag
	 * @param ab
	 * @return
	 */
	public final Spectrum set(float ar, float ag, float ab) {
		r = ar;
		g = ag;
		b = ab;
		return this;
	}

	public final Spectrum set(int argb) {
		b = (argb & 0x000000ff) / 255.0f;
		argb >>= 8;
		g = (argb & 0x0000ff) / 255.0f;
		argb >>= 8;
		r = (argb & 0x00ff) / 255.0f;
		return this;
	}

	public final int getBandsNum() {
		return 3;
	}

	/**
	 * 
	 * @param scale
	 * @param c
	 * @return
	 */
	public static Spectrum multiply(float scale, Spectrum c) {
		return new Spectrum(c.r * scale, c.g * scale, c.b * scale);
	}

	/**
	 * 
	 * @param c
	 * @param scale
	 * @return
	 */
	public static Spectrum multiply(Spectrum c, float scale) {
		return new Spectrum(c.r * scale, c.g * scale, c.b * scale);
	}

	public final Spectrum divide(float s) {
		float d = 1.0f / s;
		r *= d;
		g *= d;
		b *= d;
		return this;
	}

	public final void zeroAll() {
		r = g = b = 0.0f;
	}

	public final float average() {
		return (r + g + b) / 3;
	}

	/**
	 * 
	 * @param c
	 */
	public final void blend(Spectrum c) {
		r *= c.r;
		g *= c.g;
		b *= c.b;
	}

	/**
	 * 
	 * @param a
	 */
	public final void multiply(float a) {
		r *= a;
		b *= a;
		g *= a;
	}

	public static final Spectrum blend(Spectrum c0, Spectrum c1, Spectrum dest) {
		return dest.set(c0.r + c1.r, c0.g + c1.g, c0.b + c1.b);
	}

	/**
	 * 
	 * @param c
	 */
	public final void add(Spectrum c) {
		r += c.r;
		g += c.g;
		b += c.b;
	}

	public final void add(int argb) {
		b += (argb & 0x000000ff) / 255.0f;
		argb >>= 8;
		g += (argb & 0x0000ff) / 255.0f;
		argb >>= 8;
		r += (argb & 0x00ff) / 255.0f;
	}

	public final void add(float r, float g, float b) {
		this.r += r;
		this.g += g;
		this.b += b;
	}

	/**
	 * 
	 * @return
	 */
	public final int toARGB() {
		int ar = (int) (r * 255), ag = (int) (g * 255), ab = (int) (b * 255);
		if (ar > 255) {
			ar = 255;
		}
		if (ag > 255) {
			ag = 255;
		}
		if (ab > 255) {
			ab = 255;
		}
		return 0xff000000 | (ar << 16) | (ag << 8) | (ab);
	}

	/**
	 * 
	 */
	public final Spectrum clone() {
		return new Spectrum(r, g, b);
	}

	/**
	 * 
	 */
	public final String toString() {
		String s = "r = " + r + ", g = " + g + ", b = " + b;
		return s;
	}
}
