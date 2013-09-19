package prime.model;

import java.io.Serializable;

import prime.math.Vector;
import prime.physics.BSDF;
import prime.physics.Ray;

/**
 * triangle is the primitive, which does not belong to interactive primitives
 * 
 * @author lizhaoliu
 */
public final class Triangle implements Serializable {
	private static final long serialVersionUID = 6355696347468388740L;

	private TriangleMesh mesh; // the triangle mesh where triangle belongs
	private int index; // index in triangle mesh

	/**
	 * empty constructor
	 */
	public Triangle() {
	}

	/**
	 * set this triangle's index in its triangle mesh
	 * 
	 * @param index
	 */
	public final void setIndex(int index) {
		this.index = index;
	}

	/**
	 * get the vertex indexed i
	 * 
	 * @param i
	 * @return
	 */
	public final Vector getVertex(int i) {
		return mesh.getVertex(index, i);
	}

	/**
	 * judge if ray intersects with triangle
	 * 
	 * @param ray
	 * @param dest
	 */
	public final void intersect(Ray ray, RayIntersectionInfo dest) {
		Vector d = new Vector();
		ray.getDirection(d);
		Vector o = new Vector();
		ray.getOrigin(o);

		Vector v0 = getVertex(0);
		Vector v1 = getVertex(1);
		Vector v2 = getVertex(2);

		Vector v10 = new Vector();
		Vector.sub(v1, v0, v10);
		Vector v20 = new Vector();
		Vector.sub(v2, v0, v20);
		Vector vo0 = new Vector();
		Vector.sub(o, v0, vo0);

		float t, u, v;
		float invProduct = 1.0f / -Vector.mixproduction(v10, v20, d);

		u = -Vector.mixproduction(vo0, v20, d) * invProduct;
		if (u < 0 || u > 1) {
			dest.setIsIntersected(false);
			return;
		}

		v = -Vector.mixproduction(v10, vo0, d) * invProduct;
		if (v < 0 || v > 1) {
			dest.setIsIntersected(false);
			return;
		}

		t = Vector.mixproduction(v10, v20, vo0) * invProduct;
		if (t < 0 || t > ray.getLength()) {
			dest.setIsIntersected(false);
			return;
		}

		if (u + v > 1) {
			dest.setIsIntersected(false);
			return;
		}

		ray.setLength(t);

		dest.setIsIntersected(true);
		dest.setHitTriangle(this);
		dest.setUV(u, v);
	}

	/**
	 * judge if line segment p0-p1 intersects with triangle
	 * 
	 * @param p0
	 * @param p1
	 * @return
	 */
	public final boolean intersect(Vector p0, Vector p1) {
		Vector o = p0;

		Vector v0 = getVertex(0);
		Vector v1 = getVertex(1);
		Vector v2 = getVertex(2);

		Vector d = new Vector();
		Vector.sub(p1, p0, d);

		Vector v10 = new Vector();
		Vector.sub(v1, v0, v10);
		Vector v20 = new Vector();
		Vector.sub(v2, v0, v20);
		Vector vo0 = new Vector();
		Vector.sub(o, v0, vo0);

		float t, u, v;
		float invProduct = 1.0f / -Vector.mixproduction(v10, v20, d);

		u = -Vector.mixproduction(vo0, v20, d) * invProduct;
		if (u < 0 || u > 1) {
			return false;
		}

		v = -Vector.mixproduction(v10, vo0, d) * invProduct;
		if (v < 0 || v > 1) {
			return false;
		}

		t = Vector.mixproduction(v10, v20, vo0) * invProduct;
		if (t < 0 || t > 1) {
			return false;
		}

		if (u + v > 1) {
			return false;
		}

		return true;
	}

	/**
	 * 
	 * @param u
	 * @param v
	 * @param dest
	 * @return
	 */
	public final Vector interpolateVertex(float u, float v, Vector dest) {
		float w = 1 - u - v;
		Vector v0 = getVertex(0);
		Vector v1 = getVertex(1);
		Vector v2 = getVertex(2);
		return dest.set(w * v0.x + u * v1.x + v * v2.x, w * v0.y + u * v1.y + v
				* v2.y, w * v0.z + u * v1.z + v * v2.z);
	}

	/**
	 * 
	 * @param u
	 * @param v
	 * @param dest
	 * @return
	 */
	public final Vector interpolateNormal(float u, float v, Vector dest) {
		float w = 1 - u - v;
		Vector n0 = getNormal(0);
		Vector n1 = getNormal(1);
		Vector n2 = getNormal(2);
		dest.set(w * n0.x + u * n1.x + v * n2.x,
				w * n0.y + u * n1.y + v * n2.y, w * n0.z + u * n1.z + v * n2.z);
		dest.normalize();
		return dest;
	}

	/**
	 * 
	 * @param u
	 * @param v
	 * @param dest
	 * @return
	 */
	public final Vector interpolateTexCoord(float u, float v, Vector dest) {
		float w = 1 - u - v;
		Vector t0 = getTexCoordinate(0);
		Vector t1 = getTexCoordinate(1);
		Vector t2 = getTexCoordinate(2);
		return dest.set(w * t0.x + u * t1.x + v * t2.x, w * t0.y + u * t1.y + v
				* t2.y, w * t0.z + u * t1.z + v * t2.z);
	}

	/**
	 * 
	 * @param box
	 * @return
	 */
	public final boolean intersect(BoundingBox box) {
		Vector v0 = getVertex(0);
		Vector v1 = getVertex(1);
		Vector v2 = getVertex(2);

		if (box.contains(v0) || box.contains(v1) || box.contains(v2)) {
			return true;
		}

		if (box.intersect(v0, v1) || box.intersect(v1, v2)
				|| box.intersect(v2, v0)) {
			return true;
		}

		Vector min = new Vector();
		box.getMinPoint(min);
		Vector max = new Vector();
		box.getMaxPoint(max);
		Vector p0 = new Vector(), p1 = new Vector();

		p0.set(min);
		p1.set(max);
		if (intersect(p0, p1)) {
			return true;
		}

		p0.set(min.x, max.y, min.z);
		p1.set(max.x, min.y, max.z);
		if (intersect(p0, p1)) {
			return true;
		}

		p0.set(min.x, max.y, max.z);
		p1.set(max.x, min.y, min.z);
		if (intersect(p0, p1)) {
			return true;
		}

		p0.set(min.x, min.y, max.z);
		p1.set(max.x, max.y, min.z);
		if (intersect(p0, p1)) {
			return true;
		}

		return false;
	}

	/**
	 * 
	 * @param dest
	 * @return
	 */
	public final Vector getRandomPoint(Vector dest) {
		Vector v0 = getVertex(0);
		Vector v1 = getVertex(1);
		Vector v2 = getVertex(2);
		float u = 1 - (float) Math.sqrt(1 - Math.random()), v = (1 - u)
				* (float) Math.random(), w = 1 - u - v;
		return dest.set(v0.x * w + v1.x * u + v2.x * v, v0.y * w + v1.y * u
				+ v2.y * v, v0.z * w + v1.z * u + v2.z * v);
	}

	/**
	 * 
	 * @param v0
	 * @param v1
	 * @param v2
	 * @param dest
	 * @return
	 */
	public static final Vector getRandomPoint(Vector v0, Vector v1,
			Vector v2, Vector dest) {
		float u = 1 - (float) Math.sqrt(1 - Math.random()), v = (1 - u)
				* (float) Math.random(), w = 1 - u - v;
		return dest.set(v0.x * w + v1.x * u + v2.x * v, v0.y * w + v1.y * u
				+ v2.y * v, v0.z * w + v1.z * u + v2.z * v);
	}

	/**
	 * 
	 * @param v0
	 * @param v1
	 * @param v2
	 * @return
	 */
	public static final float getArea(Vector v0, Vector v1, Vector v2) {
		float dx1 = v1.x - v0.x, dy1 = v1.y - v0.y, dz1 = v1.z - v0.z, dx2 = v2.x
				- v0.x, dy2 = v2.y - v0.y, dz2 = v2.z - v0.z;
		float x = dy1 * dz2 - dz1 * dy2, y = dz1 * dx2 - dx1 * dz2, z = dx1
				* dy2 - dy1 * dx2;
		return (float) Math.sqrt(x * x + y * y + z * z) / 2;
	}

	/**
	 * get texture coordinate indexed i
	 * 
	 * @param i
	 * @return
	 */
	public final Vector getTexCoordinate(int i) {
		return mesh.getTexCoord(index, i);
	}

	/**
	 * 
	 * @return
	 */
	public final BSDF getBSDF() {
		return mesh.getBSDF();
	}

	/**
	 * get normal indexed i
	 * 
	 * @param i
	 * @return
	 */
	public final Vector getNormal(int i) {
		return mesh.getNormal(index, i);
	}

	/**
	 * set the mesh to which triangle belong
	 * 
	 * @param mesh
	 */
	public final void setTriangleMesh(TriangleMesh mesh) {
		this.mesh = mesh;
	}

	/**
	 * get the mesh to which triangle belong
	 * 
	 * @return
	 */
	public final TriangleMesh getTriangleMesh() {
		return mesh;
	}
}
