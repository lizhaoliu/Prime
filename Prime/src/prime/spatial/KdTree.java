package prime.spatial;

import java.io.Serializable;

import prime.math.Vec3;
import prime.model.BoundingBox;
import prime.model.RayBoxIntInfo;
import prime.model.RayTriIntInfo;
import prime.model.Triangle;
import prime.physics.Ray;

/**
 * a kd-tree implementation used to store 
 * @author lizhaoliu
 *
 */
public class KdTree extends SpatialStructure implements Serializable {
	private static final long serialVersionUID = -6629508645189976660L;
	
	private KdNode root = null;
	private int maxDivisionDepth = 10;
	private int maxTrianglesPerNode = 5;

	/**
	 * 
	 * @param box
	 * @param maxDivisionDepth
	 * @param maxTrianglesPerNode
	 */
	public KdTree(BoundingBox box, int maxDivisionDepth, int maxTrianglesPerNode) {
		super(box);
		this.maxDivisionDepth = maxDivisionDepth;
		this.maxTrianglesPerNode = maxTrianglesPerNode;
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
		if (depth < maxDivisionDepth
				&& bSPNode.box.getTriangleNum() > maxTrianglesPerNode) {
			bSPNode.subdivideAxis = axis;

			Vec3 min = bSPNode.box.getMinPoint();
			Vec3 max = bSPNode.box.getMaxPoint();
			Vec3 mid = bSPNode.box.getMidPoint();
			float r = mid.get(axis);

			bSPNode.leftChild = new KdNode();
			bSPNode.rightChild = new KdNode();
			Vec3 leftMax = new Vec3(max), rightMin = new Vec3(min);
			leftMax.set(axis, r);
			rightMin.set(axis, r);
			bSPNode.leftChild.box = new BoundingBox(min, leftMax);
			bSPNode.rightChild.box = new BoundingBox(rightMin, max);
			int nextAxis = (axis + 1) % 3;

			/*
			 * 
			 */
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
	 * @param dest
	 */
	public void intersect(Ray ray, RayTriIntInfo dest) {
		if (root == null) {
			dest.setIsIntersected(false);
			return;
		}
		intersect(ray, root, dest);
	}

	/**
	 * This method is the key to the efficiency of whole intersection process
	 * 
	 * @param ray
	 * @param kdNode
	 * @param dst
	 */
	private void intersect(Ray ray, KdNode kdNode,
			RayTriIntInfo dst) {
		RayBoxIntInfo rayBoxInt = kdNode.box.intersect(ray);
		if (!rayBoxInt.isHit()) {
			dst.setIsIntersected(false);
			return;
		}
		if (kdNode.isLeaf()) {
			kdNode.box.intersect(ray, dst);
			return;
		}

		Vec3 o = ray.getOrigin(), d = ray.getDirection();
		Vec3 mid = kdNode.box.getMidPoint();
		int axis = kdNode.subdivideAxis;
		float tmid = (mid.get(axis) - o.get(axis)) / d.get(axis);
		if (o.get(axis) < mid.get(axis)) {	// origin on the lesser side of splitting plane
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
		} else {							// origin on the greater side of splitting plane
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
		this.maxDivisionDepth = maxDivisionDepth;
	}

	public int getMaxDivisionDepth() {
		return maxDivisionDepth;
	}

	public void setMaxTrianglesPerNode(int maxTrianglesPerNode) {
		this.maxTrianglesPerNode = maxTrianglesPerNode;
	}

	public int getMaxTrianglesPerNode() {
		return maxTrianglesPerNode;
	}
}

/**
 * 
 * @author lizhaoliu
 *
 */
class KdNode implements Serializable {
	private static final long serialVersionUID = -1993087877071361426L;

	static int X = 0;
	static int Y = 1;
	static int Z = 2;

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
