package prime.physics;

import java.io.Serializable;

/**
 * 
 */
public class Color3f implements Serializable {
  private static final long serialVersionUID = -7257336123202843467L;
  public float r = 0f, g = 0f, b = 0f;

  public Color3f() {}

  public Color3f(float r, float g, float b) {
    set(r, g, b);
  }

  public Color3f(Color3f c) {
    set(c);
  }

  public Color3f(int rgb) {
    set(rgb);
  }

  /**
   * 
   * @param c
   */
  public void set(Color3f c) {
    r = c.r;
    g = c.g;
    b = c.b;
  }

  /**
   * 
   * @param r
   * @param g
   * @param b
   * @return
   */
  public Color3f set(float r, float g, float b) {
    this.r = r;
    this.g = g;
    this.b = b;
    return this;
  }

  public Color3f set(int rgb) {
    b = (rgb & 0xff) / 255.0f;
    rgb >>= 8;
    g = (rgb & 0xff) / 255.0f;
    rgb >>= 8;
    r = (rgb & 0xff) / 255.0f;
    return this;
  }

  /**
   * 
   * @param scale
   * @param c
   * @return
   */
  public static Color3f multiply(float scale, Color3f c) {
    return new Color3f(c.r * scale, c.g * scale, c.b * scale);
  }

  /**
   * 
   * @param c
   * @param scale
   * @return
   */
  public static Color3f multiply(Color3f c, float scale) {
    return new Color3f(c.r * scale, c.g * scale, c.b * scale);
  }

  public Color3f divide(float s) {
    float d = 1.0f / s;
    r *= d;
    g *= d;
    b *= d;
    return this;
  }

  public void zeroAll() {
    r = g = b = 0.0f;
  }

  public float average() {
    return (r + g + b) / 3;
  }

  /**
   * 
   * @param c
   */
  public void blend(Color3f c) {
    r *= c.r;
    g *= c.g;
    b *= c.b;
  }

  /**
   * 
   * @param a
   */
  public void multiply(float a) {
    r *= a;
    b *= a;
    g *= a;
  }

  public static Color3f blend(Color3f c0, Color3f c1, Color3f dest) {
    return dest.set(c0.r + c1.r, c0.g + c1.g, c0.b + c1.b);
  }

  /**
   * 
   * @param c
   */
  public void add(Color3f c) {
    r += c.r;
    g += c.g;
    b += c.b;
  }

  public void add(float r, float g, float b) {
    this.r += r;
    this.g += g;
    this.b += b;
  }

  /**
   * 
   * @return
   */
  public int toRgb() {
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
    return (ar << 16) | (ag << 8) | (ab);
  }

  /**
	 * 
	 */
  public Color3f clone() {
    return new Color3f(r, g, b);
  }

  /**
	 * 
	 */
  public String toString() {
    String s = "r = " + r + ", g = " + g + ", b = " + b;
    return s;
  }
}
