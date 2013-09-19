package prime.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

import prime.core.Camera;
import prime.core.Drawable;
import prime.math.MathUtils;
import prime.math.Tuple3;
import prime.math.Vec3;
import prime.physics.BSDF;
import prime.physics.Ray;
import prime.physics.Spectrum;

/**
 * a triangle mesh
 * 
 * @author lizhaoliu
 * 
 */
public class TriangleMesh implements Drawable, Serializable {
	private static final long serialVersionUID = 6079627240260644846L;

	private static int numMeshes = 0; // help to generate OpenGL display list

	private transient int thisNum;
	private transient boolean isToGenList = true;

	private int nTriangles; // number of triangles
	private float area; // the area of triangle meshs

	private List<Vec3> vertexBuffer = new ArrayList<Vec3>(); // vertices array
	private List<Vec3> normalBuffer = new ArrayList<Vec3>(); // normals array
	private List<Vec3> textureBuffer = new ArrayList<Vec3>(); // texture coordinates array

	private List<Tuple3> vertexIndexBuffer = new ArrayList<Tuple3>(); // vertices
	// index
	private List<Tuple3> normalIndexBuffer = new ArrayList<Tuple3>(); // normals
	// index
	private List<Tuple3> textureIndexBuffer = new ArrayList<Tuple3>(); // texture
	// coordinates
	// array

	private BSDF bsdf;
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

	public void addVertex(Vec3 v) {
		this.vertexBuffer.add(new Vec3(v));
	}

	public void addNormal(Vec3 n) {
		this.normalBuffer.add(new Vec3(n));
	}

	public void addTexCoord(Vec3 t) {
		this.textureBuffer.add(new Vec3(t));
	}

	public void addVertexIndex(Tuple3 vi) {
		vertexIndexBuffer.add(vi);
	}

	public void addNormalIndex(Tuple3 ni) {
		normalIndexBuffer.add(ni);
	}

	public void addTexCoordIndex(Tuple3 ti) {
		textureIndexBuffer.add(ti);
	}

	public void setVertexList(List<Vec3> v) {
		this.vertexBuffer.clear();
		this.vertexBuffer.addAll(v);
	}

	public void setNormalList(List<Vec3> n) {
		this.normalBuffer.clear();
		this.normalBuffer.addAll(n);
	}

	public void setTexCoordList(List<Vec3> t) {
		this.textureBuffer.clear();
		this.textureBuffer.addAll(t);
	}

	public void setSharedVertexList(List<Vec3> v) {
		this.vertexBuffer = v;
	}

	public void setSharedNormalList(List<Vec3> n) {
		this.normalBuffer = n;
	}

	public void setSharedTexCoordList(List<Vec3> t) {
		this.textureBuffer = t;
	}

	/**
	 * 
	 * @return
	 */
	public Triangle[] getTriangleArray() {
		Triangle[] triArray = new Triangle[nTriangles];
		for (int i = 0; i < nTriangles; i++) {
			Triangle t = new Triangle();
			t.setTriangleMesh(this);
			t.setIndex(i);
			triArray[i] = t;
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
	public Vec3 getVertex(int index, int iV) {
		return vertexBuffer.get(vertexIndexBuffer.get(index).get(iV));
	}

	/**
	 * 
	 * @param index
	 * @param iN
	 * @return
	 */
	public Vec3 getNormal(int index, int iN) {
		return normalBuffer.get(normalIndexBuffer.get(index).get(iN));
	}

	/**
	 * 
	 * @param index
	 * @param iT
	 * @return
	 */
	public Vec3 getTexCoord(int index, int iT) {
		return textureBuffer.get(textureIndexBuffer.get(index).get(iT));
	}

	/**
	 * 
	 * @param gl
	 */
	private void genList(GL2 gl) {
		Vec3 buf;
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
		Spectrum c = bsdf.getReflectance();
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
			area += Triangle.getArea(getVertex(i, 0), getVertex(i, 1),
					getVertex(i, 2));
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
	 * @param mat
	 */
	public void setBSDF(BSDF mat) {
		bsdf = mat;
	}

	/**
	 * 
	 * @return
	 */
	public BSDF getBSDF() {
		return bsdf;
	}

	/**
	 * 
	 * @param dst
	 * @return
	 */
	public Vec3 randomPoint(Vec3 dst) {
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
	public Vec3 randomNormal(Vec3 dst) {
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
		Vec3 origin = new Vec3();
		randomPoint(origin);
		dst.setOrigin(origin);
		Vec3 n = new Vec3();
		randomNormal(n);
		Vec3 normal = new Vec3();
		MathUtils.randomDirectionInHemisphere(n, normal);
		dst.setDirection(normal);
		dst.setLength(Float.MAX_VALUE);
		dst.setSpectrum(bsdf.getEmittance());
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
