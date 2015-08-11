package prime.math;

import prime.core.Camera;
import prime.core.Drawable;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
import java.io.Serializable;

import static prime.math.MathUtils.add;
import static prime.math.MathUtils.mul;
import static prime.math.MathUtils.sub;

/**
 * Left-handed coordinate system
 */
public class LHCoordinateSystem implements Drawable, Transformable, Serializable, Cloneable {
  private static final long serialVersionUID = -570828196426778877L;

  private Mat3 parentToThisMat = Mat3.UNIT;
  private Vec3f origin = Vec3f.ZERO;

  public LHCoordinateSystem() {

  }

  /**
   * Set the origin of it
   *
   * @param o
   */
  public void setOrigin(Vec3f o) {
    origin = o;
  }

  /**
   * Get the origin of it
   *
   * @return
   */
  public Vec3f getOrigin() {
    return new Vec3f(origin);
  }

  /**
   * @param data
   * @return
   */
  public float[] getMatrixArrayInColumnOrder(float[] data) {
    data[0] = parentToThisMat.m00;
    data[1] = parentToThisMat.m10;
    data[2] = parentToThisMat.m20;
    data[3] = 0;
    data[4] = parentToThisMat.m01;
    data[5] = parentToThisMat.m11;
    data[6] = parentToThisMat.m21;
    data[7] = 0;
    data[8] = parentToThisMat.m02;
    data[9] = parentToThisMat.m12;
    data[10] = parentToThisMat.m22;
    data[11] = 0;
    data[12] = origin.x;
    data[13] = origin.y;
    data[14] = origin.z;
    data[15] = 1;
    return data;
  }

  /**
   * @param data
   * @return
   */
  public float[] getInversedMatrixArrayInColumnOrder(float[] data) {
    Vec3f v = mul(origin, parentToThisMat).negate();
    data[0] = parentToThisMat.m00;
    data[1] = parentToThisMat.m01;
    data[2] = parentToThisMat.m02;
    data[3] = 0;
    data[4] = parentToThisMat.m10;
    data[5] = parentToThisMat.m11;
    data[6] = parentToThisMat.m12;
    data[7] = 0;
    data[8] = parentToThisMat.m20;
    data[9] = parentToThisMat.m21;
    data[10] = parentToThisMat.m22;
    data[11] = 0;
    data[12] = v.x;
    data[13] = v.y;
    data[14] = v.z;
    data[15] = 1;
    return data;
  }

  /**
   * @param data
   * @return
   */
  public float[] getMatrixArrayInRowOrder(float[] data) {
    data[0] = parentToThisMat.m00;
    data[1] = parentToThisMat.m01;
    data[2] = parentToThisMat.m02;
    data[3] = origin.x;
    data[4] = parentToThisMat.m10;
    data[5] = parentToThisMat.m11;
    data[6] = parentToThisMat.m12;
    data[7] = origin.y;
    data[8] = parentToThisMat.m20;
    data[9] = parentToThisMat.m21;
    data[10] = parentToThisMat.m22;
    data[11] = origin.z;
    data[12] = 0;
    data[13] = 0;
    data[14] = 0;
    data[15] = 1;
    return data;
  }

  /**
   * @param data
   * @return
   */
  public float[] getInversedMatrixArrayInRowOrder(float[] data) {
    Vec3f v = mul(origin, parentToThisMat).negate();
    data[0] = parentToThisMat.m00;
    data[1] = parentToThisMat.m10;
    data[2] = parentToThisMat.m20;
    data[3] = v.x;
    data[4] = parentToThisMat.m01;
    data[5] = parentToThisMat.m11;
    data[6] = parentToThisMat.m21;
    data[7] = v.y;
    data[8] = parentToThisMat.m02;
    data[9] = parentToThisMat.m12;
    data[10] = parentToThisMat.m22;
    data[11] = v.z;
    data[12] = 0;
    data[13] = 0;
    data[14] = 0;
    data[15] = 1;
    return data;
  }

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
  public void setParentToLocalMatrix(float t00, float t01, float t02, float t10, float t11, float t12, float t20,
                                     float t21, float t22) {
    parentToThisMat = new Mat3(t00, t01, t02, t10, t11, t12, t20, t21, t22);
  }

  /**
   * @param v
   */
  public void translateInLocal(Vec3f v) {
    float x = origin.x, y = origin.y, z = origin.z;
    origin = add(mul(parentToThisMat, v), new Vec3f(x, y, z));
  }

  /**
   *
   */
  public void translate(Vec3f v) {
    origin = add(origin, v);
  }

  /**
   *
   */
  public void rotate(Vec3f axis, float angle) {
    angle = (float) Math.PI * angle / 180;

    float x2 = axis.x * axis.x, y2 = axis.y * axis.y, z2 = axis.z * axis.z, xy = axis.x * axis.y, yz = axis.y * axis.z,
        xz = axis.x
            * axis.z, cost = (float) Math.cos(angle), sint = (float) Math.sin(angle), xsint = axis.x * sint, ysint =
        axis.y
            * sint, zsint = axis.z * sint, cost1 = 1 - cost, cost1xy = cost1 * xy, cost1yz = cost1 * yz, cost1xz = cost1
        * xz;
    Mat3 rot = new Mat3((1 - x2) * cost + x2, cost1xy + zsint, cost1xz - ysint, cost1xy - zsint, (1 - y2) * cost + y2,
        cost1yz + xsint, cost1xz + ysint, cost1yz - xsint, (1 - z2) * cost + z2);
    parentToThisMat = mul(parentToThisMat, rot);
  }

  /**
   * @param v
   * @return
   */
  public Vec3f transPointToParent(Vec3f v) {
    return add(mul(parentToThisMat, v), origin);
  }

  /**
   * @param d
   * @return
   */
  public Vec3f transVectorToParent(Vec3f d) {
    return mul(parentToThisMat, d);
  }

  /**
   * @param v
   * @return
   */
  public Vec3f transPointToLocal(Vec3f v) {
    Vec3f dest = sub(v, origin);
    return mul(dest, parentToThisMat);
  }

  /**
   * @param d
   * @return
   */
  public Vec3f transVectorToLocal(Vec3f d) {
    return mul(d, parentToThisMat);
  }

  /**
   *
   */
  public void draw(GL2 gl, GLU glu, Camera camera) {
    GLUquadric qua = glu.gluNewQuadric();
    glu.gluQuadricDrawStyle(qua, GLU.GLU_FILL);
    glu.gluQuadricNormals(qua, GLU.GLU_SMOOTH);

    float[] s = new float[4];
    float[] m = new float[16];
    gl.glPushMatrix();
    gl.glMultMatrixf(camera.getCoordinateSystem().getInversedMatrixArrayInColumnOrder(m), 0);
    gl.glMultMatrixf(getMatrixArrayInColumnOrder(m), 0);

    s[0] = 0.0f;
    s[1] = 0.0f;
    s[2] = 1.0f;
    s[3] = 1.0f;
    gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT, s, 0);
    gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, s, 0);
    glu.gluCylinder(qua, .2, .2, 5, 12, 1);
    gl.glPushMatrix();
    gl.glTranslatef(0, 0, 5);
    glu.gluCylinder(qua, .4, 0, 1, 12, 1);
    gl.glPopMatrix();

    s[0] = 0.0f;
    s[1] = 1.0f;
    s[2] = 0.0f;
    s[3] = 1.0f;
    gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT, s, 0);
    gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, s, 0);
    gl.glRotatef(90, 1, 0, 0);
    glu.gluCylinder(qua, .2, .2, 5, 12, 1);
    gl.glPushMatrix();
    gl.glTranslatef(0, 0, 5);
    glu.gluCylinder(qua, .4, 0, 1, 12, 1);
    gl.glPopMatrix();

    s[0] = 1.0f;
    s[1] = 0.0f;
    s[2] = 0.0f;
    s[3] = 1.0f;
    gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT, s, 0);
    gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, s, 0);
    gl.glRotatef(90, 0, 1, 0);
    glu.gluCylinder(qua, .2, .2, 5, 12, 1);
    gl.glPushMatrix();
    gl.glTranslatef(0, 0, 5);
    glu.gluCylinder(qua, .4, 0, 1, 12, 1);
    gl.glPopMatrix();
    gl.glPopMatrix();

    glu.gluDeleteQuadric(qua);
  }
}
