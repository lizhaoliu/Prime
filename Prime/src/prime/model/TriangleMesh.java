package prime.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

import prime.core.Camera;
import prime.core.Drawable;
import prime.math.MathTools;
import prime.math.Tuple3;
import prime.math.Vector;
import prime.physics.BSDF;
import prime.physics.Ray;
import prime.physics.Spectrum;

/**
 * a triangle mesh
 * 
 * @author lizhaoliu
 * 
 */
public final class TriangleMesh implements Drawable, Serializable {
	private static final long serialVersionUID = 6079627240260644846L;

	private static int numMeshes = 0; // help to generate OpenGL display list

	private transient int thisNum;
	private transient boolean isToGenList = true;

	private int nTriangles; // number of triangles
	private float area; // the area of triangle meshs

	private List<Vector> vertexBuffer = new ArrayList<Vector>(); // vertices
	// array
	private List<Vector> normalBuffer = new ArrayList<Vector>(); // normals
	// array
	private List<Vector> textureBuffer = new ArrayList<Vector>(); // texture
	// coordinates
	// array

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

	public final void addVertex(Vector v) {
		this.vertexBuffer.add(new Vector(v));
	}

	public final void addNormal(Vector n) {
		this.normalBuffer.add(new Vector(n));
	}

	public final void addTexCoord(Vector t) {
		this.textureBuffer.add(new Vector(t));
	}

	public final void addVertexIndex(Tuple3 vi) {
		vertexIndexBuffer.add(vi);
	}

	public final void addNormalIndex(Tuple3 ni) {
		normalIndexBuffer.add(ni);
	}

	public final void addTexCoordIndex(Tuple3 ti) {
		textureIndexBuffer.add(ti);
	}

	public final void setVertexList(List<Vector> v) {
		this.vertexBuffer.clear();
		this.vertexBuffer.addAll(v);
	}

	public final void setNormalList(List<Vector> n) {
		this.normalBuffer.clear();
		this.normalBuffer.addAll(n);
	}

	public final void setTexCoordList(List<Vector> t) {
		this.textureBuffer.clear();
		this.textureBuffer.addAll(t);
	}

	public final void setSharedVertexList(List<Vector> v) {
		this.vertexBuffer = v;
	}

	public final void setSharedNormalList(List<Vector> n) {
		this.normalBuffer = n;
	}

	public final void setSharedTexCoordList(List<Vector> t) {
		this.textureBuffer = t;
	}

	/**
	 * 
	 * @return
	 */
	public final Triangle[] getTriangleArray() {
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
	public final void setName(String name) {
		this.name = name;
	}

	/**
	 * 
	 * @return
	 */
	public final String getName() {
		return name;
	}

	/**
	 * 
	 * @param index
	 * @param iV
	 * @return
	 */
	public final Vector getVertex(int index, int iV) {
		return vertexBuffer.get(vertexIndexBuffer.get(index).get(iV));
	}

	/**
	 * 
	 * @param index
	 * @param iN
	 * @return
	 */
	public final Vector getNormal(int index, int iN) {
		return normalBuffer.get(normalIndexBuffer.get(index).get(iN));
	}

	/**
	 * 
	 * @param index
	 * @param iT
	 * @return
	 */
	public final Vector getTexCoord(int index, int iT) {
		return textureBuffer.get(textureIndexBuffer.get(index).get(iT));
	}

	/**
	 * 
	 * @param gl
	 */
	private final void genList(GL2 gl) {
		Vector buf;
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
	public final void draw(GL2 gl, GLU glu, Camera camera) {
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
	public final void finish() {
		nTriangles = vertexIndexBuffer.size();
		isToGenList = true;
		calculateArea();
	}

	/**
	 * 
	 * @return
	 */
	public final float getArea() {
		return area;
	}

	/**
	 * 
	 */
	private final void calculateArea() {
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
	public final void drawBoundingBox(GL2 gl, GLU glu, Camera camera) {
		bb.draw(gl, glu, camera);
	}

	/**
	 * 
	 * @param mat
	 */
	public final void setBSDF(BSDF mat) {
		bsdf = mat;
	}

	/**
	 * 
	 * @return
	 */
	public final BSDF getBSDF() {
		return bsdf;
	}

	/**
	 * 
	 * @param dst
	 * @return
	 */
	public final Vector randomPoint(Vector dst) {
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
	public final Vector randomNormal(Vector dst) {
		int base = (int) (Math.random() * nTriangles);
		Triangle.getRandomPoint(getNormal(base, 0), getNormal(base, 1),
				getNormal(base, 2), dst);
		return dst;
	}

	/**
	 * 
	 * @param dst
	 */
	public final void emitRandomRay(Ray dst) {
		Vector origin = new Vector();
		randomPoint(origin);
		dst.setOrigin(origin);
		Vector n = new Vector();
		randomNormal(n);
		Vector normal = new Vector();
		MathTools.randomDirectionInHemisphere(n, normal);
		dst.setDirection(normal);
		dst.setLength(Float.MAX_VALUE);
		dst.setSpectrum(bsdf.getEmittance());
	}

	/**
	 * 
	 * @return
	 */
	public final int getTrianglesNum() {
		return vertexIndexBuffer.size() - 2;
	}

	public final String toString() {
		return name;
	}
}
