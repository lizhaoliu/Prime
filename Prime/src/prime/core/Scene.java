package prime.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import prime.math.MathUtils;
import prime.math.Vec3f;
import prime.model.BoundingBox;
import prime.model.RayTriHitInfo;
import prime.model.Triangle;
import prime.model.TriangleMesh;
import prime.physics.Ray;
import prime.physics.Sky;
import prime.spatial.KdTree;

/**
 * Scene that adopts one spatial sorting algorithm to accelerate ray-triangle intersection test
 */
public class Scene implements Serializable, Iterable<TriangleMesh> {
  private static final long serialVersionUID = 4945947837143837173L;

  private List<TriangleMesh> meshList = new ArrayList<TriangleMesh>();
  private List<TriangleMesh> meshLightList = new ArrayList<TriangleMesh>();
  private int kdTreeDepth = 18;
  private int maxTrisPerLeaf = 3;

  private transient List<Triangle> triangleList = new ArrayList<Triangle>();
  private transient KdTree bSPTree;

  private Sky sky = new Sky();

  public void addMesh(TriangleMesh c) {
    meshList.add(c);
  }

  /**
   * get mesh indexed i
   * 
   * @param i
   * @return
   */
  public TriangleMesh getMesh(int i) {
    return meshList.get(i);
  }

  public TriangleMesh getLight(int index) {
    return meshLightList.get(index);
  }

  public void clearScene() {
    meshList.clear();
    meshLightList.clear();
  }

  public void removeMesh(TriangleMesh c) {
    meshList.remove(c);
    if (c.getMaterial().isLight()) {
      meshLightList.remove(c);
    }
  }

  public int getMeshNum() {
    return meshList.size();
  }

  public int getLightNum() {
    return meshLightList.size();
  }

  public void setMaxBspDivisionDepth(int bspDepth) {
    this.kdTreeDepth = bspDepth;
  }

  public int getMaxBSPDivisionDepth() {
    return kdTreeDepth;
  }

  public void setMaxTrianglesPerBSPNode(int maxTrianglePerNode) {
    this.maxTrisPerLeaf = maxTrianglePerNode;
  }

  public int getMaxTrianglesPerBSPNode() {
    return maxTrisPerLeaf;
  }

  private void genLightList() {
    meshLightList.clear();
    for (TriangleMesh t : meshList) {
      if (t.getMaterial().isLight()) {
        meshLightList.add(t);
      }
    }
  }

  boolean isVisible(Vec3f p0, Vec3f p1) {
    Ray ray = new Ray();
    ray.setLengthToMax();
    Vec3f d = Vec3f.sub(p1, p0);
    d.normalize();
    ray.setDirection(d);
    ray.setOrigin(p0.x + d.x * MathUtils.EPSILON, p0.y + d.y * MathUtils.EPSILON, p0.z + d.z * MathUtils.EPSILON);
    RayTriHitInfo ir = new RayTriHitInfo();
    bSPTree.intersect(ray, ir);
    return ir.isHit();
  }

  public int getTrianglesNum() {
    return triangleList.size();
  }

  /**
   * 
   * @return
   */
  public Sky getSky() {
    return sky;
  }

  /**
   * 
   * @param ray
   * @param dest
   */
  public void intersect(Ray ray, RayTriHitInfo dest) {
    if (bSPTree == null) {
      return;
    }
    bSPTree.intersect(ray, dest);
  }

  public void finish() {
    genLightList();

    triangleList.clear();
    for (TriangleMesh mesh : meshList) {
      Triangle[] tArray = mesh.getTriangleArray();
      for (Triangle t : tArray) {
        triangleList.add(t);
      }
    }

    BoundingBox box = new BoundingBox();
    for (Triangle t : triangleList) {
      box.add(t);
    }
    box.adjustSize();
    bSPTree = new KdTree(box, kdTreeDepth, maxTrisPerLeaf);
  }

  @Override
  public Iterator<TriangleMesh> iterator() {
    // TODO Auto-generated method stub
    return meshList.iterator();
  }
}
