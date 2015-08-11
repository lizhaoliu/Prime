package prime.math;

import java.io.Serializable;

/**
 * 3x3 matrix
 *
 * @author lizhaoliu
 */
public class Mat3 implements Serializable {
  private static final long serialVersionUID = 7865171386308719777L;

  public static final Mat3 UNIT = new Mat3(1, 0, 0, 0, 1, 0, 0, 0, 1);

  /**
   *
   */
  float m00, m01, m02, m10, m11, m12, m20, m21, m22;

  /**
   * @param t00
   * @param t01
   * @param t02
   * @param t10
   * @param t11
   * @param t12
   * @param t20
   * @param t21
   * @param t22
   */
  public Mat3(float t00, float t01, float t02, float t10, float t11, float t12, float t20, float t21, float t22) {
    m00 = t00;
    m01 = t01;
    m02 = t02;
    m10 = t10;
    m11 = t11;
    m12 = t12;
    m20 = t20;
    m21 = t21;
    m22 = t22;
  }
}
