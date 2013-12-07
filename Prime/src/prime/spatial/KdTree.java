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
     * @param bSPNode
     * @param depth
     * @param axis
     */
    private void subdiv(KdNode bSPNode, int depth, int axis) {
	if (depth < maxDepth && bSPNode.box.getTriangleNum() > maxTrianglesPerLeaf) {
	    bSPNode.subdivideAxis = axis;

	    Vec3f min = bSPNode.box.getMinPoint();
	    Vec3f max = bSPNode.box.getMaxPoint();
	    Vec3f mid = bSPNode.box.getMidPoint();
	    float r = mid.get(axis);

	    bSPNode.leftChild = new KdNode();
	    bSPNode.rightChild = new KdNode();
	    Vec3f leftMax = new Vec3f(max), rightMin = new Vec3f(min);
	    leftMax.set(axis, r);
	    rightMin.set(axis, r);
	    bSPNode.leftChild.box = new BoundingBox(min, leftMax);
	    bSPNode.rightChild.box = new BoundingBox(rightMin, max);
	    int nextAxis = (axis + 1) % 3;

	    for (int i = 0; i < bSPNode.box.getTriangleNum(); i++) {
		Triangle t = bSPNode.box.getTriangle(i);
		if (t.intersect((bSPNode.leftChild.box))) {
		    bSPNode.leftChild.add(t);
		}
		if (t.intersect((bSPNode.rightChild.box))) {
		    bSPNode.rightChild.add(t);
		}
	    }
	    bSPNode.box.clear();

	    subdiv(bSPNode.leftChild, depth + 1, nextAxis);
	    subdiv(bSPNode.rightChild, depth + 1, nextAxis);
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
		intersect(ray, kdNode.leftChild, dst);
	    } else if (tmid < rayBoxInt.getMin()) {
		intersect(ray, kdNode.rightChild, dst);
	    } else {
		intersect(ray, kdNode.leftChild, dst);
		if (!dst.isHit()) {
		    intersect(ray, kdNode.rightChild, dst);
		}
	    }
	} else { // origin on the greater side of splitting plane
	    if (tmid < 0 || tmid > rayBoxInt.getMax()) {
		intersect(ray, kdNode.rightChild, dst);
	    } else if (tmid < rayBoxInt.getMin()) {
		intersect(ray, kdNode.leftChild, dst);
	    } else {
		intersect(ray, kdNode.rightChild, dst);
		if (!dst.isHit()) {
		    intersect(ray, kdNode.leftChild, dst);
		}
	    }

	}
    }

    public void setMaxDivisionDepth(int maxDivisionDepth) {
	this.maxDepth = maxDivisionDepth;
    }

    public int getMaxDivisionDepth() {
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
	KdNode leftChild, rightChild;
	int subdivideAxis;

	public void add(Triangle t) {
	    box.add(t);
	}

	public boolean isLeaf() {
	    return leftChild == null && rightChild == null;
	}
    }
}
