package prime.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

import prime.core.Camera;
import prime.core.Drawable;
import prime.math.Vec3;
import prime.physics.Ray;

/**
 * axis-aligned bounding box (AABB) which contains a list of triangles
 * 
 * @author lizhaoliu
 */
public class BoundingBox implements Drawable, Iterable<Triangle>,
		Serializable {
	private static final long serialVersionUID = -8072891542252256281L;
	private Vec3 min = new Vec3(), max = new Vec3();
	private List<Triangle> triangleList = new ArrayList<Triangle>();

	public BoundingBox() {
	}

	public BoundingBox(Vec3 minV, Vec3 maxV) {
		min.set(minV);
		max.set(maxV);
	}

	public BoundingBox(float minX, float minY, float minZ, float maxX,
			float maxY, float maxZ) {
		min.set(minX, minY, minZ);
		max.set(maxX, maxY, maxZ);
	}

	/**
	 * to tighten the box
	 */
	public void adjustSize() {
		min.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
				Float.POSITIVE_INFINITY);
		max.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
				Float.NEGATIVE_INFINITY);
		Vec3 buf;
		for (Triangle t : triangleList) {
			for (int j = 0; j < 3; j++) {
				buf = t.getVertex(j);
				for (int k = 0; k < 3; k++) {
					if (min.get(k) > buf.get(k)) {
						min.set(k, buf.get(k));
					}
					if (max.get(k) < buf.get(k)) {
						max.set(k, buf.get(k));
					}
				}
			}
		}
	}

	/**
	 * 
	 * @param p
	 */
	public void add(Triangle p) {
		triangleList.add(p);
	}

	/**
	 * 
	 * @param minX
	 * @param minY
	 * @param minZ
	 * @param maxX
	 * @param maxY
	 * @param maxZ
	 */
	public void set(float minX, float minY, float minZ, float maxX,
			float maxY, float maxZ) {
		min.set(minX, minY, minZ);
		max.set(maxX, maxY, maxZ);
	}

	/**
	 * 
	 * @param min
	 * @param max
	 */
	public void set(Vec3 min, Vec3 max) {
		this.min.set(min);
		this.max.set(max);
	}

	/**
	 * 
	 */
	public void clear() {
		triangleList.clear();
	}

	/**
	 * 
	 * @param index
	 * @return
	 */
	public Triangle getTriangle(int index) {
		return triangleList.get(index);
	}

	/**
	 * 
	 * @return
	 */
	public int getTriangleNum() {
		return triangleList.size();
	}

	/**
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
	 * ray-intersection test
	 * @param ray
	 * @return
	 */
	public boolean intersect(Ray ray) {
		float tNear = Float.NEGATIVE_INFINITY, tFar = Float.POSITIVE_INFINITY, t1, t2;
		Vec3 o = new Vec3();
		ray.getOrigin(o);
		Vec3 d = new Vec3();
		ray.getDirection(d);
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
			if (tNear > tFar || tFar < 0 || tNear > ray.getLength()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 
	 * @param v1
	 * @param v2
	 * @return
	 */
	public boolean intersect(Vec3 v1, Vec3 v2) {
		float tNear = Float.NEGATIVE_INFINITY, tFar = Float.POSITIVE_INFINITY, t1, t2;
		Vec3 o = v1, d = new Vec3(v2.x - v1.x, v2.y - v1.y, v2.z - v1.z);
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
	 * ray-intersection, and store the intersection result
	 * @param ray
	 * @param dst
	 */
	public void intersect(Ray ray, RayIntersectionInfo dst) {
		RayIntersectionInfo tmp = new RayIntersectionInfo();
		for (int i = 0; i < triangleList.size(); i++) {
			triangleList.get(i).intersect(ray, tmp);
			if (tmp.isIntersected()) {
				dst.assign(tmp);
			}
		}
	}

	/**
	 * test if inside
	 * @param v
	 * @return
	 */
	public boolean contains(Vec3 v) {
		for (int i = 0; i < 3; i++) {
			if (v.get(i) > max.get(i) || v.get(i) < min.get(i)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 
	 * @param dst
	 */
	public void getMidPoint(Vec3 dst) {
		dst.set((min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2);
	}

	/**
	 * 
	 * @param dst
	 */
	public void getMinPoint(Vec3 dst) {
		dst.set(min);
	}

	/**
	 * 
	 * @param dst
	 */
	public void getMaxPoint(Vec3 dst) {
		dst.set(max);
	}

	/**
	 * 
	 * @return
	 */
	public int maxLengthAxis() {
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
	 * 
	 * @param i
	 * @return
	 */
	public float getLength(int i) {
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
		gl.glMultMatrixf(camera.getCoordinateSystem()
				.getInversedMatrixArrayInColumnOrder(s), 0);

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
		String s = "min : " + min + "; max : " + max + "\n" + "dx = "
				+ (max.x - min.x) + ", dy = " + (max.y - min.y) + ", dz = "
				+ (max.z - min.z);
		return s;
	}

	@Override
	public Iterator<Triangle> iterator() {
		// TODO Auto-generated method stub
		return triangleList.iterator();
	}
}