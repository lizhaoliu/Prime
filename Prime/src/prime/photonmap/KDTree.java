package prime.photonmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import prime.math.Vector;
import prime.model.BoundingBox;

/**
 * a kd-tree implementation which stores photons
 * @author lizhaoliu
 *
 */
public final class KDTree {
	private KDNode head;

	public KDTree(List<Photon> pList) {
		head = new KDNode(pList, 0);
		subdivide(head);
	}

	public final void query(BoundingBox region, List<Photon> resList) {
		query1(region, head, resList);
	}

	public final void query(Vector center, float r, List<Photon> resList) {
		query2(center, r, head, resList);
	}

	/**
	 * 
	 * @param p
	 * @param initR
	 * @param n
	 * @param resList
	 */
	public final float nearestNeighbors(Vector p, float initR, int n,
			List<Photon> resList) {
		query(p, initR, resList);
		while (resList.size() < n) {
			initR *= 2;
			resList.clear();
			query(p, initR, resList);
		}
		Collections.sort(resList, new DistanceComparator(p));
		return Vector.distance(p, resList.get(n - 1).location);
	}

	private final void query2(Vector center, float r, KDNode currNode,
			List<Photon> resList) {
		int axis = currNode.subdivisionAxis;
		float midValue = currNode.midValue;
		if (currNode.isLeaf()) {
			Photon p;
			List<Photon> pList = currNode.pList;
			for (int i = 0; i < pList.size(); i++) {
				p = pList.get(i);
				if (Vector.distance(p.location, center) < r) {
					resList.add(p);
				}
			}
		} else {
			if (center.get(axis) - r > midValue) {
				query2(center, r, currNode.right, resList);
			} else if (center.get(axis) + r < midValue) {
				query2(center, r, currNode.left, resList);
			} else {
				query2(center, r, currNode.right, resList);
				query2(center, r, currNode.left, resList);
			}
		}
	}

	private final void query1(BoundingBox region, KDNode currNode,
			List<Photon> resList) {
		Vector min = new Vector();
		region.getMinPoint(min);
		Vector max = new Vector();
		region.getMaxPoint(max);
		int axis = currNode.subdivisionAxis;
		float midValue = currNode.midValue;
		if (currNode.isLeaf()) {
			Photon p;
			List<Photon> pList = currNode.pList;
			for (int i = 0; i < pList.size(); i++) {
				p = pList.get(i);
				if (region.contains(p.location)) {
					resList.add(p);
				}
			}
		} else {
			if (min.get(axis) > midValue) {
				query1(region, currNode.right, resList);
			} else if (max.get(axis) < midValue) {
				query1(region, currNode.left, resList);
			} else {
				query1(region, currNode.right, resList);
				query1(region, currNode.left, resList);
			}
		}
	}

	private final void subdivide(KDNode currNode) {
		List<Photon> pList = currNode.pList;
		int axis = currNode.subdivisionAxis;
		if (pList.size() <= 1) {
			return;
		}
		Collections.sort(pList, new AxisComparator(axis));
		int median = pList.size() / 2;
		int nextAxis = (axis + 1) % 3;
		float midValue = pList.get(median).location.get(axis);
		currNode.midValue = midValue;
		List<Photon> leftList = new ArrayList<Photon>(), rightList = new ArrayList<Photon>();
		for (int i = 0; i < median; i++) {
			leftList.add(pList.get(i));
		}
		for (int i = median; i < pList.size(); i++) {
			rightList.add(pList.get(i));
		}
		pList.clear();
		currNode.left = new KDNode(leftList, nextAxis);
		currNode.right = new KDNode(rightList, nextAxis);
		subdivide(currNode.left);
		subdivide(currNode.right);
	}
}

final class KDNode {
	List<Photon> pList;
	int subdivisionAxis;
	float midValue;
	KDNode left, right;

	public KDNode(List<Photon> pList, int axis) {
		this.pList = pList;
		subdivisionAxis = axis;
	}

	public final boolean isLeaf() {
		return (left == null && right == null);
	}
}

final class DistanceComparator implements Comparator<Photon> {
	private Vector center;

	public DistanceComparator(Vector center) {
		this.center = center;
	}

	public final int compare(Photon o1, Photon o2) {
		float d1 = Vector.distanceSqr(o1.location, center), d2 = Vector
				.distanceSqr(o2.location, center);
		if (d1 < d2) {
			return -1;
		}
		if (d1 > d2) {
			return 1;
		}
		return 0;
	}
}

final class AxisComparator implements Comparator<Photon> {
	private int axis;

	public AxisComparator(int axis) {
		this.axis = axis;
	}

	public final int compare(Photon o1, Photon o2) {
		if (o1.location.get(axis) < o2.location.get(axis)) {
			return -1;
		}
		if (o1.location.get(axis) > o2.location.get(axis)) {
			return 1;
		}
		return 0;
	}
}