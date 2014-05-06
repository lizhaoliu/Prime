package prime.spatial;

import java.io.Serializable;

import prime.math.Vec3f;
import prime.model.BoundingBox;
import prime.model.RayBoxIntInfo;
import prime.model.RayTriIntInfo;
import prime.model.Triangle;
import prime.physics.Ray;

/**
 * A kd-tree implementation
 * 
 * @author lizhaoliu
 * 
 */
public class KdTree extends SpatialStructure implements Serializable {
  private static final long serialVersionUID = -6629508645189976660L;

  private KdNode root = null;
  private int maxDepth = 10;
  private int maxTrianglesPerLeaf = 5;

  /**
   * 
   * @param box
   * @param maxDepth
   * @param maxTrianglesPerLeaf
   */
  public KdTree(BoundingBox box, int maxDepth, int maxTrianglesPerLeaf) {
    super(box);
    this.maxDepth = maxDepth;
    this.maxTrianglesPerLeaf = maxTrianglesPerLeaf;

    root = new KdNode();
    root.box = box;
    subdiv();
  }

  /**
   * subdivide the structure
   */
  public void subdiv() {
    subdiv(root, 0, root.box.maxLengthAxis());
  }

  /**
   * the implementation of subdivision the KD tree
   * 
   * @param node
   * @param depth
   * @param axis
   */
  private void subdiv(KdNode node, int depth, int axis) {
    if (depth < maxDepth && node.box.getTriangleNum() > maxTrianglesPerLeaf) {
      node.subdivideAxis = axis;

      Vec3f min = node.box.getMinPoint();
      Vec3f max = node.box.getMaxPoint();
      Vec3f mid = node.box.getMidPoint();
      float r = mid.get(axis);

      node.left = new KdNode();
      node.right = new KdNode();
      Vec3f leftMax = new Vec3f(max), rightMin = new Vec3f(min);
      leftMax.set(axis, r);
      rightMin.set(axis, r);
      node.left.box = new BoundingBox(min, leftMax);
      node.right.box = new BoundingBox(rightMin, max);
      int nextAxis = (axis + 1) % 3;

      for (int i = 0; i < node.box.getTriangleNum(); i++) {
        Triangle t = node.box.getTriangle(i);
        if (t.intersect((node.left.box))) {
          node.left.add(t);
        }
        if (t.intersect((node.right.box))) {
          node.right.add(t);
        }
      }
      node.box.clear();

      subdiv(node.left, depth + 1, nextAxis);
      subdiv(node.right, depth + 1, nextAxis);
    }
  }

  /**
   * 
   * @param ray
   * @param dst
   */
  public void intersect(Ray ray, RayTriIntInfo dst) {
    if (root == null) {
      dst.setIsIntersected(false);
      return;
    }
    intersect(ray, root, dst);
  }

  /**
   * This method is the key to the efficiency of whole intersection process
   * 
   * @param ray
   * @param kdNode
   * @param dst
   */
  private void intersect(Ray ray, KdNode kdNode, RayTriIntInfo dst) {
    RayBoxIntInfo rayBoxInt = kdNode.box.intersect(ray);
    if (!rayBoxInt.isHit()) {
      dst.setIsIntersected(false);
      return;
    }
    if (kdNode.isLeaf()) {
      kdNode.box.intersect(ray, dst);
      return;
    }

    Vec3f o = ray.getOrigin(), d = ray.getDirection();
    Vec3f mid = kdNode.box.getMidPoint();
    int axis = kdNode.subdivideAxis;
    float tmid = (mid.get(axis) - o.get(axis)) / d.get(axis);
    if (o.get(axis) < mid.get(axis)) { // origin on the lesser side of
      // splitting plane
      if (tmid < 0 || tmid > rayBoxInt.getMax()) {
        intersect(ray, kdNode.left, dst);
      } else if (tmid < rayBoxInt.getMin()) {
        intersect(ray, kdNode.right, dst);
      } else {
        intersect(ray, kdNode.left, dst);
        if (!dst.isHit()) {
          intersect(ray, kdNode.right, dst);
        }
      }
    } else { // origin on the greater side of splitting plane
      if (tmid < 0 || tmid > rayBoxInt.getMax()) {
        intersect(ray, kdNode.right, dst);
      } else if (tmid < rayBoxInt.getMin()) {
        intersect(ray, kdNode.left, dst);
      } else {
        intersect(ray, kdNode.right, dst);
        if (!dst.isHit()) {
          intersect(ray, kdNode.left, dst);
        }
      }

    }
  }

  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxTrianglesPerNode(int maxTrianglesPerNode) {
    this.maxTrianglesPerLeaf = maxTrianglesPerNode;
  }

  public int getMaxTrianglesPerNode() {
    return maxTrianglesPerLeaf;
  }

  /**
	 * 
	 */
  private static final class KdNode implements Serializable {
    private static final long serialVersionUID = -1993087877071361426L;

    BoundingBox box;
    KdNode left, right;
    int subdivideAxis;

    public void add(Triangle t) {
      box.add(t);
    }

    public boolean isLeaf() {
      return left == null && right == null;
    }
  }
}
