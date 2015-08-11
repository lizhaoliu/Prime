package prime.model;

import prime.core.Camera;
import prime.core.Drawable;
import prime.math.Vec3f;
import prime.physics.Ray;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Axis-aligned bounding box (AABB) which contains a list of triangles
 */
public class BoundingBox implements Drawable, Iterable<Triangle>, Serializable {
  private static final long serialVersionUID = -8072891542252256281L;

  private Vec3f min = Vec3f.ZERO, max = Vec3f.ZERO;
  private List<Triangle> triangleList = new ArrayList<>();

  public BoundingBox() {
  }

  public BoundingBox(Vec3f minV, Vec3f maxV) {
    this.min = minV;
    this.max = maxV;
  }

  public BoundingBox(float min, float max) {
    this.min = new Vec3f(min, min, min);
    this.max = new Vec3f(max, max, max);
  }

  public BoundingBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    min = new Vec3f(minX, minY, minZ);
    max = new Vec3f(maxX, maxY, maxZ);
  }

  /**
   * to tighten the box
   */
  public void adjustSize() {
    float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
    float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
    for (Triangle t : triangleList) {
      for (int j = 0; j < 3; j++) {
        Vec3f v = t.getVertex(j);
        for (int k = 0; k < 3; k++) {
          min[k] = Math.min(min[k], v.get(k));
          max[k] = Math.max(max[k], v.get(k));
        }
      }
    }
    this.min = new Vec3f(min[0], min[1], min[2]);
    this.max = new Vec3f(max[0], max[1], max[2]);
  }

  /**
   * @param p
   */
  public void add(Triangle p) {
    triangleList.add(p);
  }

  /**
   * @param minX
   * @param minY
   * @param minZ
   * @param maxX
   * @param maxY
   * @param maxZ
   */
  public void set(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    min = new Vec3f(minX, minY, minZ);
    max = new Vec3f(maxX, maxY, maxZ);
  }

  /**
   * @param min
   * @param max
   */
  public void set(Vec3f min, Vec3f max) {
    this.min = min;
    this.max = max;
  }

  /**
   *
   */
  public void clear() {
    triangleList.clear();
  }

  /**
   * @param index
   * @return
   */
  public Triangle getTriangle(int index) {
    return triangleList.get(index);
  }

  /**
   * @return
   */
  public int getTriangleNum() {
    return triangleList.size();
  }

  /**
   * Test if two bounding boxes intersect with each other
   *
   * @param box
   * @return
   */
  public boolean intersect(BoundingBox box) {
    for (int i = 0; i < 3; i++) {
      if (max.get(i) < box.min.get(i) || box.max.get(i) < min.get(i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Test if the ray intersects with this bounding box
   *
   * @param ray
   * @return
   */
  public RayBoxIntInfo intersect(Ray ray) {
    float tNear = Float.NEGATIVE_INFINITY, tFar = Float.POSITIVE_INFINITY, t1, t2;
    Vec3f o = ray.getOrigin();
    Vec3f d = ray.getDirection();
    for (int i = 0; i < 3; i++) {
      t1 = (min.get(i) - o.get(i)) / d.get(i);
      t2 = (max.get(i) - o.get(i)) / d.get(i);
      if (t1 > t2) { // swap t1 and t2
        float tmp = t1;
        t1 = t2;
        t2 = tmp;
      }
      if (tNear < t1) {
        tNear = t1;
      }
      if (tFar > t2) {
        tFar = t2;
      }
      if (tNear > tFar || tFar < 0 || tNear > ray.getLength()) {
        return new RayBoxIntInfo(false, t1, t2);
      }
    }
    return new RayBoxIntInfo(true, tNear, tFar);
  }

  /**
   * Test if the line segment v1-v2 intersects with the bounding box
   *
   * @param v1
   * @param v2
   * @return
   */
  public boolean intersect(Vec3f v1, Vec3f v2) {
    float tNear = Float.NEGATIVE_INFINITY, tFar = Float.POSITIVE_INFINITY, t1, t2;
    Vec3f o = v1, d = new Vec3f(v2.x - v1.x, v2.y - v1.y, v2.z - v1.z);
    for (int i = 0; i < 3; i++) {
      t1 = (min.get(i) - o.get(i)) / d.get(i);
      t2 = (max.get(i) - o.get(i)) / d.get(i);
      if (t1 > t2) {
        float temp = t1;
        t1 = t2;
        t2 = temp;
      }
      if (tNear < t1) {
        tNear = t1;
      }
      if (tFar > t2) {
        tFar = t2;
      }
      if (tNear > tFar || tFar < 0 || tNear > 1) {
        return false;
      }
    }
    return true;
  }

  /**
   * Perform ray-triangle intersection test for each {@link Triangle} inside
   *
   * @param ray
   * @return
   */
  public RayTriHitInfo intersectRayWithTriangles(Ray ray) {
    RayTriHitInfo hitInfo = new RayTriHitInfo();
    for (Triangle t : triangleList) {
      RayTriHitInfo tmp = t.intersect(ray);
      if (tmp.isHit()) {
        hitInfo = tmp;
      }
    }
    return hitInfo;
  }

  /**
   * Test if a point is inside the bounding box
   *
   * @param v
   * @return
   */
  public boolean contains(Vec3f v) {
    for (int i = 0; i < 3; i++) {
      if (v.get(i) > max.get(i) || v.get(i) < min.get(i)) {
        return false;
      }
    }
    return true;
  }

  /**
   *
   */
  public Vec3f getMidPoint() {
    return new Vec3f((min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2);
  }

  /**
   *
   */
  public Vec3f getMinPoint() {
    return new Vec3f(min);
  }

  /**
   *
   */
  public Vec3f getMaxPoint() {
    return new Vec3f(max);
  }

  /**
   * Return the axis with the greatest extent
   *
   * @return
   */
  public int maxExtentAxis() {
    float dx = max.x - min.x, dy = max.y - min.y, dz = max.z - min.z;
    int axle = 0;
    if (dy > dx) {
      axle = 1;
      if (dz > dy) {
        axle = 2;
      }
    }
    if (dz > dx) {
      axle = 2;
      if (dy > dz) {
        axle = 1;
      }
    }
    return axle;
  }

  /**
   * Get the extent on a particular axis
   *
   * @param i <p>
   *          0: x axis<br/>
   *          1: y axis<br/>
   *          2: z axis<br/>
   *          </p>
   * @return
   */
  public float getExtent(int i) {
    switch (i) {
      case 0:
        return max.x - min.x;

      case 1:
        return max.y - min.y;

      case 2:
        return max.z - min.z;

      default:
        throw new ArrayIndexOutOfBoundsException();
    }
  }

  /**
   * If the bounding box contains no {@link Triangle}
   *
   * @return
   */
  public boolean isEmpty() {
    return (triangleList.isEmpty());
  }

  /**
   *
   */
  public void draw(GL2 gl, GLU glu, Camera camera) {
    float[] s = new float[16];
    gl.glPushMatrix();
    gl.glLoadIdentity();
    gl.glMultMatrixf(camera.getCoordinateSystem().getInversedMatrixArrayInColumnOrder(s), 0);

    gl.glDisable(GL2.GL_LIGHTING);
    gl.glBegin(GL2.GL_LINES);
    gl.glColor3f(0f, 1f, 0f);

    gl.glVertex3f(min.x, min.y, min.z);
    gl.glVertex3f(max.x, min.y, min.z);
    gl.glVertex3f(min.x, max.y, min.z);
    gl.glVertex3f(max.x, max.y, min.z);
    gl.glVertex3f(min.x, max.y, max.z);
    gl.glVertex3f(max.x, max.y, max.z);
    gl.glVertex3f(min.x, min.y, max.z);
    gl.glVertex3f(max.x, min.y, max.z);

    gl.glVertex3f(min.x, min.y, min.z);
    gl.glVertex3f(min.x, max.y, min.z);
    gl.glVertex3f(max.x, min.y, min.z);
    gl.glVertex3f(max.x, max.y, min.z);
    gl.glVertex3f(max.x, min.y, max.z);
    gl.glVertex3f(max.x, max.y, max.z);
    gl.glVertex3f(min.x, min.y, max.z);
    gl.glVertex3f(min.x, max.y, max.z);

    gl.glVertex3f(min.x, min.y, min.z);
    gl.glVertex3f(min.x, min.y, max.z);
    gl.glVertex3f(max.x, min.y, min.z);
    gl.glVertex3f(max.x, min.y, max.z);
    gl.glVertex3f(max.x, max.y, min.z);
    gl.glVertex3f(max.x, max.y, max.z);
    gl.glVertex3f(min.x, max.y, min.z);
    gl.glVertex3f(min.x, max.y, max.z);
    gl.glEnd();
    gl.glPopMatrix();
  }

  @Override
  public String toString() {
    String s = "min : " + min + "; max : " + max + "\n" + "dx = " + (max.x - min.x) + ", dy = " + (max.y - min.y)
        + ", dz = " + (max.z - min.z);
    return s;
  }

  @Override
  public Iterator<Triangle> iterator() {
    // TODO Auto-generated method stub
    return triangleList.iterator();
  }
}