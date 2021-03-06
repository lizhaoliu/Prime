package prime.photonmap;

import prime.math.Vec3f;
import prime.model.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static prime.math.MathUtils.dist;
import static prime.math.MathUtils.distSqr;

/**
 * a kd-tree implementation to store photons
 *
 * @author lizhaoliu
 */
public class KdTree {
  private KdNode head;

  public KdTree(List<Photon> pList) {
    head = new KdNode(pList, 0);
    subdivide(head);
  }

  public void query(BoundingBox region, List<Photon> resList) {
    query1(region, head, resList);
  }

  public void query(Vec3f center, float r, List<Photon> resList) {
    query2(center, r, head, resList);
  }

  /**
   * @param p
   * @param initR
   * @param n
   * @param resList
   */
  public float nearestNeighbors(Vec3f p, float initR, int n, List<Photon> resList) {
    query(p, initR, resList);
    while (resList.size() < n) {
      initR *= 2;
      resList.clear();
      query(p, initR, resList);
    }
    Collections.sort(resList, new DistanceComparator(p));
    return dist(p, resList.get(n - 1).location);
  }

  private void query2(Vec3f center, float r, KdNode currNode, List<Photon> resList) {
    int axis = currNode.subdivisionAxis;
    float midValue = currNode.midValue;
    if (currNode.isLeaf()) {
      Photon p;
      List<Photon> pList = currNode.pList;
      for (int i = 0; i < pList.size(); i++) {
        p = pList.get(i);
        if (dist(p.location, center) < r) {
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

  private void query1(BoundingBox region, KdNode currNode, List<Photon> resList) {
    Vec3f min = region.getMinPoint();
    Vec3f max = region.getMaxPoint();
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

  private void subdivide(KdNode currNode) {
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
    currNode.left = new KdNode(leftList, nextAxis);
    currNode.right = new KdNode(rightList, nextAxis);
    subdivide(currNode.left);
    subdivide(currNode.right);
  }
}

class KdNode {
  List<Photon> pList;
  int subdivisionAxis;
  float midValue;
  KdNode left, right;

  public KdNode(List<Photon> pList, int axis) {
    this.pList = pList;
    subdivisionAxis = axis;
  }

  public boolean isLeaf() {
    return (left == null && right == null);
  }
}

class DistanceComparator implements Comparator<Photon> {
  private Vec3f center;

  public DistanceComparator(Vec3f center) {
    this.center = center;
  }

  public int compare(Photon o1, Photon o2) {
    float d1 = distSqr(o1.location, center), d2 = distSqr(o2.location, center);
    if (d1 < d2) {
      return -1;
    }
    if (d1 > d2) {
      return 1;
    }
    return 0;
  }
}

class AxisComparator implements Comparator<Photon> {
  private int axis;

  public AxisComparator(int axis) {
    this.axis = axis;
  }

  public int compare(Photon o1, Photon o2) {
    if (o1.location.get(axis) < o2.location.get(axis)) {
      return -1;
    }
    if (o1.location.get(axis) > o2.location.get(axis)) {
      return 1;
    }
    return 0;
  }
}