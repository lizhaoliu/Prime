package prime.spatial;

import java.io.Serializable;

import prime.math.Vec3;
import prime.model.BoundingBox;
import prime.model.RayIntersectionInfo;
import prime.model.Triangle;
import prime.physics.Ray;

/**
 * a kd-tree implementation used to store 
 * @author lizhaoliu
 *
 */
public class KdTree extends SpatialStructure implements Serializable {
	private static long serialVersionUID = 6185494279455270385L;
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
		subdivide();
	}

	/**
	 * subdivide the structure
	 */
	public void subdivide() {
		subdivideImplementation(root, 0, root.box.maxLengthAxis());
	}

	/**
	 * the implementation of subdivision the KD tree
	 * 
	 * @param bSPNode
	 * @param depth
	 * @param axis
	 */
	private void subdivideImplementation(KdNode bSPNode, int depth, int axis) {
		if (depth < maxDivisionDepth
				&& bSPNode.box.getTriangleNum() > maxTrianglesPerNode) {
			bSPNode.subdivideAxis = axis;

			Vec3 min = new Vec3();
			bSPNode.box.getMinPoint(min);
			Vec3 max = new Vec3();
			bSPNode.box.getMaxPoint(max);
			Vec3 mid = new Vec3();
			bSPNode.box.getMidPoint(mid);
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

			subdivideImplementation(bSPNode.leftChild, depth + 1, nextAxis);
			subdivideImplementation(bSPNode.rightChild, depth + 1, nextAxis);
		}
	}

	/**
	 * 
	 * @param ray
	 * @param dest
	 */
	public void intersect(Ray ray, RayIntersectionInfo dest) {
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
	 * @param bSPNode
	 * @param dest
	 */
	private void intersect(Ray ray, KdNode bSPNode,
			RayIntersectionInfo dest) {
		if (!bSPNode.box.intersect(ray)) {
			dest.setIsIntersected(false);
			return;
		}
		if (bSPNode.leftChild == null && bSPNode.rightChild == null) // a leaf
		{
			bSPNode.box.intersect(ray, dest);
			return;
		}

		RayIntersectionInfo intr = new RayIntersectionInfo();
		intersect(ray, bSPNode.leftChild, dest);
		float destLength = ray.getLength();
		intersect(ray, bSPNode.rightChild, intr);
		float intrLength = ray.getLength();
		if (intr.isIntersected()) {
			if (dest.isIntersected()) {
				if (destLength > intrLength) {
					dest.assign(intr);
				}
			} else {
				dest.assign(intr);
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
}
