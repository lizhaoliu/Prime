package prime.math;

import java.io.Serializable;

/**
 * 3x3 matrix
 * 
 * @author lizhaoliu
 */
public class Mat3 implements Serializable {
  private static final long serialVersionUID = 7865171386308719777L;

  /**
	 * 
	 */
  float m00, m01, m02, m10, m11, m12, m20, m21, m22;

  /**
     * 
     */
  public Mat3() {
    m00 = 1;
    m11 = 1;
    m22 = 1;
  }

  /**
   * 
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

  /**
   * 
   * @param mat
   */
  public Mat3(Mat3 mat) {
    m00 = mat.m00;
    m00 = mat.m01;
    m02 = mat.m02;
    m10 = mat.m10;
    m11 = mat.m11;
    m12 = mat.m12;
    m20 = mat.m20;
    m21 = mat.m21;
    m22 = mat.m22;
  }

  /**
   * 
   * @param m
   * @return
   */
  public Mat3 set(float[] m) {
    m00 = m[0];
    m01 = m[1];
    m02 = m[2];
    m10 = m[3];
    m11 = m[4];
    m12 = m[5];
    m20 = m[6];
    m21 = m[7];
    m22 = m[8];
    return this;
  }

  /**
   * 
   * @param mat
   * @return
   */
  public Mat3 set(Mat3 mat) {
    m00 = mat.m00;
    m01 = mat.m01;
    m02 = mat.m02;
    m10 = mat.m10;
    m11 = mat.m11;
    m12 = mat.m12;
    m20 = mat.m20;
    m21 = mat.m21;
    m22 = mat.m22;
    return this;
  }

  /**
   * 
   * @param t00
   * @param t01
   * @param t02
   * @param t10
   * @param t11
   * @param t12
   * @param t20
   * @param t21
   * @param t22
   * @return
   */
  public Mat3 set(float t00, float t01, float t02, float t10, float t11, float t12, float t20, float t21, float t22) {
    m00 = t00;
    m01 = t01;
    m02 = t02;
    m10 = t10;
    m11 = t11;
    m12 = t12;
    m20 = t20;
    m21 = t21;
    m22 = t22;
    return this;
  }

  /**
   * 
   * @return
   */
  public Mat3 transpose() {
    return set(m00, m10, m20, m01, m11, m21, m02, m12, m22);
  }

  /**
   * 
   * @param m
   * @param dest
   * @return
   */
  public static Mat3 transpose(Mat3 m) {
    return new Mat3(m.m00, m.m10, m.m20, m.m01, m.m11, m.m21, m.m02, m.m12, m.m22);
  }
}
