package prime.model;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

import org.apache.commons.collections.CollectionUtils;

import prime.core.Camera;
import prime.core.Drawable;
import prime.math.MathUtils;
import prime.math.Vec3f;
import prime.math.Vec3i;
import prime.physics.Color3f;
import prime.physics.Material;
import prime.physics.Ray;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * TriangleMesh is a 3D model consisted of {@link Triangle}s
 */
public final class TriangleMesh implements Drawable, Serializable {
  private static final long serialVersionUID = 6079627240260644846L;

  private static AtomicInteger globalMaxId = new AtomicInteger(); // OpenGL display list id

  private transient final int id;
  private transient boolean isDisplayListCreated;

  private final List<Vec3f> vertexList;
  private final List<Vec3f> normalList;
  private final List<Vec3f> texCoordList;

  private final List<Vec3i> vertexIndexList;
  private final List<Vec3i> normalIndexList;
  private final List<Vec3i> texCoordsIndexList;

  private final String name;

  private final int nTriangles; // number of triangles
  private final float area; // the area of triangle meshs

  private Material material;
  private Triangle[] triangleArray;

  private BoundingBox aabb = new BoundingBox(Float.MAX_VALUE, Float.MIN_VALUE);
  private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = Float.MIN_VALUE,
      maxY = Float.MIN_VALUE, maxZ = Float.MIN_VALUE;

  private TriangleMesh(Builder builder) {
    id = globalMaxId.incrementAndGet();

    vertexList = builder.vertexList;
    normalList = builder.normalList;
    texCoordList = builder.texCoordList;

    vertexIndexList = builder.vertexIndexList;
    normalIndexList = builder.normalIndexList;
    texCoordsIndexList = builder.texCoordIndexList;

    name = builder.name;

    nTriangles = vertexIndexList.size();
    area = calculateArea();
    
    triangleArray = createTriangleArray();
  }

  /**
   * 
   * @return
   */
  public Triangle[] getTriangleArray() {
    return triangleArray;
  }
  
  private Triangle[] createTriangleArray() {
    Triangle[] triArray = new Triangle[nTriangles];
    for (int i = 0; i < nTriangles; i++) {
      triArray[i] = new Triangle(this, i);
    }
    return triArray;
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
   * @param triangleId
   * @param vertexId
   * @return
   */
  Vec3f getVertex(int triangleId, int vertexId) {
    return vertexList.get(vertexIndexList.get(triangleId).get(vertexId));
  }

  /**
   * 
   * @param triangleId
   * @param normalId
   * @return
   */
  Vec3f getNormal(int triangleId, int normalId) {
    return normalList.get(normalIndexList.get(triangleId).get(normalId));
  }

  /**
   * 
   * @param triangleId
   * @param texCoordId
   * @return
   */
  Vec3f getTexCoord(int triangleId, int texCoordId) {
    return texCoordList.get(texCoordsIndexList.get(triangleId).get(texCoordId));
  }

  /**
   * 
   * @param gl
   */
  private void genDisplayList(GL2 gl) {
    Vec3f buf;
    gl.glNewList(id, GL2.GL_COMPILE);
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

    aabb.set(minX, minY, minZ, maxX, maxY, maxZ);
  }

  /**
   * draw by OpenGL
   */
  @Override
  public void draw(GL2 gl, GLU glu, Camera camera) {
    if (!isDisplayListCreated) {
      genDisplayList(gl);
      isDisplayListCreated = true;
    }

    float[] s = new float[16];
    gl.glPushMatrix();
    gl.glLoadIdentity();
    gl.glMultMatrixf(camera.getCoordinateSystem().getInversedMatrixArrayInColumnOrder(s), 0);
    Color3f c = material.getReflectance();
    s[0] = c.r;
    s[1] = c.g;
    s[2] = c.b;
    s[3] = 1.0f;
    gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT, s, 0);
    gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, s, 0);
    gl.glMaterialf(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, 50.0f);
    gl.glCallList(id);
    gl.glPopMatrix();
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
  private float calculateArea() {
    float area = 0.0f;
    for (int i = 0; i < nTriangles; i++) {
      area += Triangle.getArea(getVertex(i, 0), getVertex(i, 1), getVertex(i, 2));
    }
    return area;
  }

  /**
   * 
   * @param gl
   * @param glu
   * @param camera
   */
  public void drawBoundingBox(GL2 gl, GLU glu, Camera camera) {
    aabb.draw(gl, glu, camera);
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
   * @return
   */
  public Vec3f randomPoint() {
    int base = (int) (MathUtils.random() * nTriangles);
    return Triangle.getRandomPoint(getVertex(base, 0), getVertex(base, 1), getVertex(base, 2));
  }

  /**
   * 
   * @return
   */
  public Vec3f randomNormal(Vec3f dst) {
    int base = (int) (MathUtils.random() * nTriangles);
    return Triangle.getRandomPoint(getNormal(base, 0), getNormal(base, 1), getNormal(base, 2));
  }

  /**
   * 
   * @param dst
   */
  public void emitRandomRay(Ray dst) {
    Vec3f origin = randomPoint();
    dst.setOrigin(origin);
    Vec3f n = new Vec3f();
    randomNormal(n);
    Vec3f normal = MathUtils.randomDirectionInHemisphere(n);
    dst.setDirection(normal);
    dst.setLength(Float.MAX_VALUE);
    dst.setSpectrum(material.getEmittance());
  }

  /**
   * 
   * @return
   */
  public int getTriangleCount() {
    return vertexIndexList.size();
  }
  
  /**
   * 
   * @return
   */
  public int getPositionCount() {
    return vertexList.size();
  }
  
  /**
   * 
   * @return
   */
  public int getNormalCount() {
    return normalList.size();
  }
  
  /**
   * 
   * @return
   */
  public int getTexCoordCount() {
    return texCoordList.size();
  }

  public String toString() {
    return name;
  }

  /**
   * Builder for {@link TriangleMesh}
   */
  public static class Builder {
    private List<Vec3f> vertexList;
    private List<Vec3f> normalList;
    private List<Vec3f> texCoordList;

    private List<Vec3i> vertexIndexList;
    private List<Vec3i> normalIndexList;
    private List<Vec3i> texCoordIndexList;

    private String name;

    public Builder withVertexList(final List<Vec3f> vertexList) {
      this.vertexList = ImmutableList.copyOf(vertexList);
      return this;
    }

    public Builder withNormalList(final List<Vec3f> normalList) {
      this.normalList = ImmutableList.copyOf(normalList);
      return this;
    }

    public Builder withTexCoordList(final List<Vec3f> texCoordList) {
      this.texCoordList = ImmutableList.copyOf(texCoordList);
      return this;
    }

    public Builder withVertexIndexList(final List<Vec3i> vertexIndexList) {
      this.vertexIndexList = ImmutableList.copyOf(vertexIndexList);
      return this;
    }

    public Builder withNormalIndexList(final List<Vec3i> normalIndexList) {
      this.normalIndexList = ImmutableList.copyOf(normalIndexList);
      return this;
    }

    public Builder withTexCoordIndexList(final List<Vec3i> texCoordIndexList) {
      this.texCoordIndexList = ImmutableList.copyOf(texCoordIndexList);
      return this;
    }

    public Builder withName(final String name) {
      this.name = name;
      return this;
    }

    public TriangleMesh build() {
      validate();
      TriangleMesh triangleMesh = new TriangleMesh(this);
      return triangleMesh;
    }

    private void validate() {
      Preconditions.checkState(!CollectionUtils.isEmpty(vertexList));
      Preconditions.checkState(!CollectionUtils.isEmpty(normalList));
      Preconditions.checkState(!CollectionUtils.isEmpty(vertexIndexList));
      Preconditions.checkState(!CollectionUtils.isEmpty(normalIndexList));
    }
  }
}
