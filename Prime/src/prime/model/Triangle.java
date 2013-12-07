package prime.model;

import java.io.Serializable;

import prime.math.Vec3f;
import prime.physics.Material;
import prime.physics.Ray;

/**
 * Triangles are the atoms of 3D world
 */
public class Triangle implements Serializable {
    private static final long serialVersionUID = 6355696347468388740L;

    private final TriangleMesh triangleMesh; //the triangle mesh where triangle belongs
    private final int index; 	//index in triangle mesh
    private final float area;	//area of this triangle

    /**
     * 
     * @param triangleMesh the {@link TriangleMesh} this triangle belongs to
     * @param index the index of this triangle in its triangle mesh
     */
    public Triangle(final TriangleMesh triangleMesh, int index) {
	this.triangleMesh = triangleMesh;
	this.index = index;
	area = calcArea();
    }

    /**
     * Get the vertex indexed i
     * 
     * @param i {0, 1, 2}
     * @return
     */
    public Vec3f getVertex(int i) {
	return triangleMesh.getVertex(index, i);
    }

    /**
     * Ray triangle intersection test
     * 
     * @param ray
     * @param dst
     */
    public void intersect(final Ray ray, final RayTriIntInfo dst) {
	Vec3f d = ray.getDirection();
	Vec3f o = ray.getOrigin();

	Vec3f v0 = getVertex(0);
	Vec3f v1 = getVertex(1);
	Vec3f v2 = getVertex(2);

	Vec3f v10 = Vec3f.sub(v1, v0);
	Vec3f v20 = Vec3f.sub(v2, v0);
	Vec3f vo0 = Vec3f.sub(o, v0);

	float t, u, v;
	float invProduct = 1.0f / -Vec3f.tripleProduct(v10, v20, d);

	u = -Vec3f.tripleProduct(vo0, v20, d) * invProduct;
	if (u < 0 || u > 1) {
	    dst.setIsIntersected(false);
	    return;
	}

	v = -Vec3f.tripleProduct(v10, vo0, d) * invProduct;
	if (v < 0 || v > 1) {
	    dst.setIsIntersected(false);
	    return;
	}

	t = Vec3f.tripleProduct(v10, v20, vo0) * invProduct;
	if (t < 0 || t > ray.getLength()) {
	    dst.setIsIntersected(false);
	    return;
	}

	if (u + v > 1) {
	    dst.setIsIntersected(false);
	    return;
	}

	ray.setLength(t);

	dst.setIsIntersected(true);
	dst.setHitTriangle(this);
	dst.setUV(u, v);
    }

    /**
     * Line segment p0-p1 triangle intersection test
     * 
     * @param p0
     * @param p1
     * @return
     */
    public boolean intersect(final Vec3f p0, final Vec3f p1) {
	Vec3f o = p0;

	Vec3f v0 = getVertex(0);
	Vec3f v1 = getVertex(1);
	Vec3f v2 = getVertex(2);

	Vec3f d = Vec3f.sub(p1, p0);

	Vec3f v10 = Vec3f.sub(v1, v0);
	Vec3f v20 = Vec3f.sub(v2, v0);
	Vec3f vo0 = Vec3f.sub(o, v0);

	float t, u, v;
	float invProduct = 1.0f / -Vec3f.tripleProduct(v10, v20, d);

	u = -Vec3f.tripleProduct(vo0, v20, d) * invProduct;
	if (u < 0 || u > 1) {
	    return false;
	}

	v = -Vec3f.tripleProduct(v10, vo0, d) * invProduct;
	if (v < 0 || v > 1) {
	    return false;
	}

	t = Vec3f.tripleProduct(v10, v20, vo0) * invProduct;
	if (t < 0 || t > 1) {
	    return false;
	}

	if (u + v > 1) {
	    return false;
	}

	return true;
    }

    /**
     * Bi-linearly interpolate a position vector given u, v
     * 
     * @param u
     * @param v
     * @param dest
     * @return
     */
    public Vec3f interpolatePosition(float u, float v) {
	float w = 1 - u - v;
	Vec3f v0 = getVertex(0);
	Vec3f v1 = getVertex(1);
	Vec3f v2 = getVertex(2);
	return new Vec3f(w * v0.x + u * v1.x + v * v2.x, w * v0.y + u * v1.y
		+ v * v2.y, w * v0.z + u * v1.z + v * v2.z);
    }

    /**
     * Bi-linearly interpolate a normal vector given u, v
     * 
     * @param u
     * @param v
     * @param dest
     * @return
     */
    public Vec3f interpolateNormal(float u, float v) {
	float w = 1 - u - v;
	Vec3f n0 = getNormal(0);
	Vec3f n1 = getNormal(1);
	Vec3f n2 = getNormal(2);
	Vec3f dest = new Vec3f(w * n0.x + u * n1.x + v * n2.x, w * n0.y + u
		* n1.y + v * n2.y, w * n0.z + u * n1.z + v * n2.z);
	dest.normalize();
	return dest;
    }

    /**
     * Bi-linearly interpolate a texture coordinate vector given u, v
     * 
     * @param u
     * @param v
     * @param dest
     * @return
     */
    public Vec3f interpolateTexCoord(float u, float v, Vec3f dest) {
	float w = 1 - u - v;
	Vec3f t0 = getTexCoordinate(0);
	Vec3f t1 = getTexCoordinate(1);
	Vec3f t2 = getTexCoordinate(2);
	return dest.set(w * t0.x + u * t1.x + v * t2.x, w * t0.y + u * t1.y + v
		* t2.y, w * t0.z + u * t1.z + v * t2.z);
    }

    /**
     * Triangle bounding box intersection test
     * 
     * @param box
     * @return
     */
    public boolean intersect(BoundingBox box) {
	Vec3f v0 = getVertex(0);
	Vec3f v1 = getVertex(1);
	Vec3f v2 = getVertex(2);

	if (box.contains(v0) || box.contains(v1) || box.contains(v2)) {
	    return true;
	}

	if (box.intersect(v0, v1) || box.intersect(v1, v2)
		|| box.intersect(v2, v0)) {
	    return true;
	}

	Vec3f min = box.getMinPoint();
	Vec3f max = box.getMaxPoint();
	Vec3f p0 = new Vec3f(), p1 = new Vec3f();

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
     * Get a random position vector on the triangle
     * 
     * @param dest
     * @return
     */
    public Vec3f getRandomPoint(Vec3f dest) {
	Vec3f v0 = getVertex(0);
	Vec3f v1 = getVertex(1);
	Vec3f v2 = getVertex(2);
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
    public static Vec3f getRandomPoint(Vec3f v0, Vec3f v1, Vec3f v2, Vec3f dest) {
	float u = 1 - (float) Math.sqrt(1 - Math.random()), v = (1 - u)
		* (float) Math.random(), w = 1 - u - v;
	return dest.set(v0.x * w + v1.x * u + v2.x * v, v0.y * w + v1.y * u
		+ v2.y * v, v0.z * w + v1.z * u + v2.z * v);
    }

    /**
     * Calculate the area of this triangle
     * 
     * @param v0
     * @param v1
     * @param v2
     * @return
     */
    public static float getArea(Vec3f v0, Vec3f v1, Vec3f v2) {
	float dx1 = v1.x - v0.x, dy1 = v1.y - v0.y, dz1 = v1.z - v0.z, dx2 = v2.x
		- v0.x, dy2 = v2.y - v0.y, dz2 = v2.z - v0.z;
	float x = dy1 * dz2 - dz1 * dy2, y = dz1 * dx2 - dx1 * dz2, z = dx1
		* dy2 - dy1 * dx2;
	return (float) Math.sqrt(x * x + y * y + z * z) / 2;
    }

    /**
     * Get texture coordinate indexed i
     * 
     * @param i
     * @return
     */
    public Vec3f getTexCoordinate(int i) {
	return triangleMesh.getTexCoord(index, i);
    }

    /**
     * Get the material of its {@link TriangleMesh}
     * 
     * @return
     */
    public Material getMaterial() {
	return triangleMesh.getMaterial();
    }

    /**
     * Get normal vector indexed i
     * 
     * @param i
     * @return
     */
    public Vec3f getNormal(int i) {
	return triangleMesh.getNormal(index, i);
    }

    /**
     * Get the {@link TriangleMesh} to which triangle belong
     * 
     * @return
     */
    public TriangleMesh getTriangleMesh() {
	return triangleMesh;
    }
    
    /**
     * 
     * @return area of this triangle
     */
    public float getArea() {
	return area;
    }

    private float calcArea() {
	Vec3f v0 = getVertex(0), v1 = getVertex(1), v2 = getVertex(2);
	float dx1 = v1.x - v0.x, dy1 = v1.y - v0.y, dz1 = v1.z - v0.z, dx2 = v2.x
		- v0.x, dy2 = v2.y - v0.y, dz2 = v2.z - v0.z;
	float x = dy1 * dz2 - dz1 * dy2, y = dz1 * dx2 - dx1 * dz2, z = dx1
		* dy2 - dy1 * dx2;
	return (float) Math.sqrt(x * x + y * y + z * z) / 2;
    }
}
