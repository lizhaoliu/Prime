package prime.model;

import java.io.Serializable;
import java.util.List;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

import prime.core.Camera;
import prime.core.Drawable;
import prime.math.MathUtils;
import prime.math.Vec3f;
import prime.math.Vec3i;
import prime.physics.Material;
import prime.physics.Ray;
import prime.physics.Color3f;

import com.google.common.collect.Lists;

/**
 * TriangleMesh is a 3D model consisted of {@link Triangle} 
 */
public class TriangleMesh implements Drawable, Serializable {
	private static final long serialVersionUID = 6079627240260644846L;

	private static int numMeshes = 0; // help to generate OpenGL display list

	private transient int thisNum;
	private transient boolean isToGenList = true;

	private int nTriangles; // number of triangles
	private float area; 	// the area of triangle meshs

	private List<Vec3f> vertexBuffer = Lists.newArrayList(); // vertices array
	private List<Vec3f> normalBuffer = Lists.newArrayList(); // normals array
	private List<Vec3f> texCoordBuffer = Lists.newArrayList(); // texture coordinates array

	private List<Vec3i> vertexIndexBuffer = Lists.newArrayList(); // vertex array index buffer
	private List<Vec3i> normalIndexBuffer = Lists.newArrayList(); // normals
	private List<Vec3i> texCoordsIndexBuffer = Lists.newArrayList(); // texture

	private Material material;
	private String name;

	private BoundingBox bb = new BoundingBox(Float.MAX_VALUE, Float.MAX_VALUE,
			Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE);
	private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE,
			minZ = Float.MAX_VALUE, maxX = Float.MIN_VALUE,
			maxY = Float.MIN_VALUE, maxZ = Float.MIN_VALUE;

	/**
	 * 
	 */
	public TriangleMesh() {
		thisNum = ++numMeshes;
	}

	public void addVertex(Vec3f v) {
		this.vertexBuffer.add(new Vec3f(v));
	}

	public void addNormal(Vec3f n) {
		this.normalBuffer.add(new Vec3f(n));
	}

	public void addTexCoord(Vec3f t) {
		this.texCoordBuffer.add(new Vec3f(t));
	}

	public void addVertexIndex(Vec3i vi) {
		vertexIndexBuffer.add(vi);
	}

	public void addNormalIndex(Vec3i ni) {
		normalIndexBuffer.add(ni);
	}

	public void addTexCoordIndex(Vec3i ti) {
		texCoordsIndexBuffer.add(ti);
	}

	public void setVertexList(List<Vec3f> v) {
		this.vertexBuffer.clear();
		this.vertexBuffer.addAll(v);
	}

	public void setNormalList(List<Vec3f> n) {
		this.normalBuffer.clear();
		this.normalBuffer.addAll(n);
	}

	public void setTexCoordList(List<Vec3f> t) {
		this.texCoordBuffer.clear();
		this.texCoordBuffer.addAll(t);
	}

	public void setSharedVertexList(List<Vec3f> v) {
		this.vertexBuffer = v;
	}

	public void setSharedNormalList(List<Vec3f> n) {
		this.normalBuffer = n;
	}

	public void setSharedTexCoordList(List<Vec3f> t) {
		this.texCoordBuffer = t;
	}

	/**
	 * 
	 * @return
	 */
	public Triangle[] getTriangleArray() {
		Triangle[] triArray = new Triangle[nTriangles];
		for (int i = 0; i < nTriangles; i++) {
			triArray[i] = new Triangle(this, i);
		}
		return triArray;
	}

	/**
	 * 
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * 
	 * @return
	 */
	public String getName() {
		return name;
	}

	/**
	 * 
	 * @param index
	 * @param iV
	 * @return
	 */
	public Vec3f getVertex(int index, int iV) {
		return vertexBuffer.get(vertexIndexBuffer.get(index).get(iV));
	}

	/**
	 * 
	 * @param index
	 * @param iN
	 * @return
	 */
	public Vec3f getNormal(int index, int iN) {
		return normalBuffer.get(normalIndexBuffer.get(index).get(iN));
	}

	/**
	 * 
	 * @param index
	 * @param iT
	 * @return
	 */
	public Vec3f getTexCoord(int index, int iT) {
		return texCoordBuffer.get(texCoordsIndexBuffer.get(index).get(iT));
	}

	/**
	 * 
	 * @param gl
	 */
	private void genList(GL2 gl) {
		Vec3f buf;
		gl.glNewList(thisNum, GL2.GL_COMPILE);
		gl.glBegin(GL2.GL_TRIANGLES);
		for (int i = 0; i < nTriangles; i++) // iterate all faces, the ith faces
		{
			buf = getNormal(i, 0);
			gl.glNormal3f(buf.x, buf.y, buf.z);
			buf = getVertex(i, 0);
			gl.glVertex3f(buf.x, buf.y, buf.z);
			//
			minX = Math.min(buf.x, minX);
			minY = Math.min(buf.y, minY);
			minZ = Math.min(buf.z, minZ);
			maxX = Math.max(buf.x, maxX);
			maxY = Math.max(buf.y, maxY);
			maxZ = Math.max(buf.z, maxZ);

			buf = getNormal(i, 1);
			gl.glNormal3f(buf.x, buf.y, buf.z);
			buf = getVertex(i, 1);
			gl.glVertex3f(buf.x, buf.y, buf.z);
			//
			minX = Math.min(buf.x, minX);
			minY = Math.min(buf.y, minY);
			minZ = Math.min(buf.z, minZ);
			maxX = Math.max(buf.x, maxX);
			maxY = Math.max(buf.y, maxY);
			maxZ = Math.max(buf.z, maxZ);

			buf = getNormal(i, 2);
			gl.glNormal3f(buf.x, buf.y, buf.z);
			buf = getVertex(i, 2);
			gl.glVertex3f(buf.x, buf.y, buf.z);
			//
			minX = Math.min(buf.x, minX);
			minY = Math.min(buf.y, minY);
			minZ = Math.min(buf.z, minZ);
			maxX = Math.max(buf.x, maxX);
			maxY = Math.max(buf.y, maxY);
			maxZ = Math.max(buf.z, maxZ);
		}
		gl.glEnd();
		gl.glEndList();

		bb.set(minX, minY, minZ, maxX, maxY, maxZ);
	}

	/**
	 * draw by OpenGL
	 */
	@Override
	public void draw(GL2 gl, GLU glu, Camera camera) {
		if (isToGenList) {
			genList(gl);
			isToGenList = false;
		}

		float[] s = new float[16];
		gl.glPushMatrix();
		gl.glLoadIdentity();
		gl.glMultMatrixf(camera.getCoordinateSystem()
				.getInversedMatrixArrayInColumnOrder(s), 0);
		Color3f c = material.getReflectance();
		s[0] = c.r;
		s[1] = c.g;
		s[2] = c.b;
		s[3] = 1.0f;
		gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT, s, 0);
		gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, s, 0);
		gl.glMaterialf(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, 50.0f);
		gl.glCallList(thisNum);
		gl.glPopMatrix();
	}

	/**
	 * finish adding info to this mesh and do some ending job
	 */
	public void finish() {
		nTriangles = vertexIndexBuffer.size();
		isToGenList = true;
		calculateArea();
	}

	/**
	 * 
	 * @return
	 */
	public float getArea() {
		return area;
	}

	/**
	 * 
	 */
	private void calculateArea() {
		area = 0.0f;
		for (int i = 0; i < nTriangles; i++) {
			area += Triangle.getArea(getVertex(i, 0), getVertex(i, 1), getVertex(i, 2));
		}
	}

	/**
	 * 
	 * @param gl
	 * @param glu
	 * @param camera
	 */
	public void drawBoundingBox(GL2 gl, GLU glu, Camera camera) {
		bb.draw(gl, glu, camera);
	}

	/**
	 * 
	 * @param material
	 */
	public void setMaterial(Material material) {
		this.material = material;
	}

	/**
	 * 
	 * @return
	 */
	public Material getMaterial() {
		return material;
	}

	/**
	 * 
	 * @param dst
	 * @return
	 */
	public Vec3f randomPoint(Vec3f dst) {
		int base = (int) (Math.random() * nTriangles);
		Triangle.getRandomPoint(getVertex(base, 0), getVertex(base, 1),
				getVertex(base, 2), dst);
		return dst;
	}

	/**
	 * 
	 * @param dst
	 * @return
	 */
	public Vec3f randomNormal(Vec3f dst) {
		int base = (int) (Math.random() * nTriangles);
		Triangle.getRandomPoint(getNormal(base, 0), getNormal(base, 1),
				getNormal(base, 2), dst);
		return dst;
	}

	/**
	 * 
	 * @param dst
	 */
	public void emitRandomRay(Ray dst) {
		Vec3f origin = new Vec3f();
		randomPoint(origin);
		dst.setOrigin(origin);
		Vec3f n = new Vec3f();
		randomNormal(n);
		Vec3f normal = new Vec3f();
		MathUtils.randomDirectionInHemisphere(n, normal);
		dst.setDirection(normal);
		dst.setLength(Float.MAX_VALUE);
		dst.setSpectrum(material.getEmittance());
	}

	/**
	 * 
	 * @return
	 */
	public int getTrianglesNum() {
		return vertexIndexBuffer.size() - 2;
	}

	public String toString() {
		return name;
	}
}
