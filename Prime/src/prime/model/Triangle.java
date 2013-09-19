package prime.model;

import java.io.Serializable;

import prime.math.Vec3;
import prime.physics.BSDF;
import prime.physics.Ray;

/**
 * triangle is the primitive, which does not belong to interactive primitives
 * 
 * @author lizhaoliu
 */
public class Triangle implements Serializable {
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
	public void setIndex(int index) {
		this.index = index;
	}

	/**
	 * get the vertex indexed i
	 * 
	 * @param i
	 * @return
	 */
	public Vec3 getVertex(int i) {
		return mesh.getVertex(index, i);
	}

	/**
	 * judge if ray intersects with triangle
	 * 
	 * @param ray
	 * @param dest
	 */
	public void intersect(Ray ray, RayIntersectionInfo dest) {
		Vec3 d = new Vec3();
		ray.getDirection(d);
		Vec3 o = new Vec3();
		ray.getOrigin(o);

		Vec3 v0 = getVertex(0);
		Vec3 v1 = getVertex(1);
		Vec3 v2 = getVertex(2);

		Vec3 v10 = new Vec3();
		Vec3.sub(v1, v0, v10);
		Vec3 v20 = new Vec3();
		Vec3.sub(v2, v0, v20);
		Vec3 vo0 = new Vec3();
		Vec3.sub(o, v0, vo0);

		float t, u, v;
		float invProduct = 1.0f / -Vec3.mixproduction(v10, v20, d);

		u = -Vec3.mixproduction(vo0, v20, d) * invProduct;
		if (u < 0 || u > 1) {
			dest.setIsIntersected(false);
			return;
		}

		v = -Vec3.mixproduction(v10, vo0, d) * invProduct;
		if (v < 0 || v > 1) {
			dest.setIsIntersected(false);
			return;
		}

		t = Vec3.mixproduction(v10, v20, vo0) * invProduct;
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
	public boolean intersect(Vec3 p0, Vec3 p1) {
		Vec3 o = p0;

		Vec3 v0 = getVertex(0);
		Vec3 v1 = getVertex(1);
		Vec3 v2 = getVertex(2);

		Vec3 d = new Vec3();
		Vec3.sub(p1, p0, d);

		Vec3 v10 = new Vec3();
		Vec3.sub(v1, v0, v10);
		Vec3 v20 = new Vec3();
		Vec3.sub(v2, v0, v20);
		Vec3 vo0 = new Vec3();
		Vec3.sub(o, v0, vo0);

		float t, u, v;
		float invProduct = 1.0f / -Vec3.mixproduction(v10, v20, d);

		u = -Vec3.mixproduction(vo0, v20, d) * invProduct;
		if (u < 0 || u > 1) {
			return false;
		}

		v = -Vec3.mixproduction(v10, vo0, d) * invProduct;
		if (v < 0 || v > 1) {
			return false;
		}

		t = Vec3.mixproduction(v10, v20, vo0) * invProduct;
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
	public Vec3 interpolateVertex(float u, float v, Vec3 dest) {
		float w = 1 - u - v;
		Vec3 v0 = getVertex(0);
		Vec3 v1 = getVertex(1);
		Vec3 v2 = getVertex(2);
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
	public Vec3 interpolateNormal(float u, float v, Vec3 dest) {
		float w = 1 - u - v;
		Vec3 n0 = getNormal(0);
		Vec3 n1 = getNormal(1);
		Vec3 n2 = getNormal(2);
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
	public Vec3 interpolateTexCoord(float u, float v, Vec3 dest) {
		float w = 1 - u - v;
		Vec3 t0 = getTexCoordinate(0);
		Vec3 t1 = getTexCoordinate(1);
		Vec3 t2 = getTexCoordinate(2);
		return dest.set(w * t0.x + u * t1.x + v * t2.x, w * t0.y + u * t1.y + v
				* t2.y, w * t0.z + u * t1.z + v * t2.z);
	}

	/**
	 * 
	 * @param box
	 * @return
	 */
	public boolean intersect(BoundingBox box) {
		Vec3 v0 = getVertex(0);
		Vec3 v1 = getVertex(1);
		Vec3 v2 = getVertex(2);

		if (box.contains(v0) || box.contains(v1) || box.contains(v2)) {
			return true;
		}

		if (box.intersect(v0, v1) || box.intersect(v1, v2)
				|| box.intersect(v2, v0)) {
			return true;
		}

		Vec3 min = new Vec3();
		box.getMinPoint(min);
		Vec3 max = new Vec3();
		box.getMaxPoint(max);
		Vec3 p0 = new Vec3(), p1 = new Vec3();

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
	public Vec3 getRandomPoint(Vec3 dest) {
		Vec3 v0 = getVertex(0);
		Vec3 v1 = getVertex(1);
		Vec3 v2 = getVertex(2);
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
	public static Vec3 getRandomPoint(Vec3 v0, Vec3 v1,
			Vec3 v2, Vec3 dest) {
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
	public static float getArea(Vec3 v0, Vec3 v1, Vec3 v2) {
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
	public Vec3 getTexCoordinate(int i) {
		return mesh.getTexCoord(index, i);
	}

	/**
	 * 
	 * @return
	 */
	public BSDF getBSDF() {
		return mesh.getBSDF();
	}

	/**
	 * get normal indexed i
	 * 
	 * @param i
	 * @return
	 */
	public Vec3 getNormal(int i) {
		return mesh.getNormal(index, i);
	}

	/**
	 * set the mesh to which triangle belong
	 * 
	 * @param mesh
	 */
	public void setTriangleMesh(TriangleMesh mesh) {
		this.mesh = mesh;
	}

	/**
	 * get the mesh to which triangle belong
	 * 
	 * @return
	 */
	public TriangleMesh getTriangleMesh() {
		return mesh;
	}
}
