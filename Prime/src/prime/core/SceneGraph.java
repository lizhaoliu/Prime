package prime.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import prime.math.MathTools;
import prime.math.Vector;
import prime.model.BoundingBox;
import prime.model.RayIntersectionInfo;
import prime.model.Triangle;
import prime.model.TriangleMesh;
import prime.physics.Ray;
import prime.physics.Sky;
import prime.spatial.KdTree;

/**
 * Scene Graph that adopts one spatial sorting algorithm to accelerate
 * ray-triangle intersection test
 * 
 * @author lizhaoliu
 */
public final class SceneGraph implements Serializable, Iterable<TriangleMesh> {
	private static final long serialVersionUID = 4945947837143837173L;

	private List<TriangleMesh> meshList = new ArrayList<TriangleMesh>();
	private List<TriangleMesh> meshLightList = new ArrayList<TriangleMesh>();
	private int bspDepth = 18;
	private int maxTrianglesPerNode = 3;

	private transient List<Triangle> triangleList = new ArrayList<Triangle>();
	private transient KdTree bSPTree;

	private Sky sky = new Sky();

	public final void addMesh(TriangleMesh c) {
		meshList.add(c);
	}

	/**
	 * get mesh indexed i
	 * 
	 * @param i
	 * @return
	 */
	public final TriangleMesh getMesh(int i) {
		return meshList.get(i);
	}

	public final TriangleMesh getLight(int index) {
		return meshLightList.get(index);
	}

	public final void clearScene() {
		meshList.clear();
		meshLightList.clear();
	}

	public final void removeMesh(TriangleMesh c) {
		meshList.remove(c);
		if (c.getBSDF().isLight()) {
			meshLightList.remove(c);
		}
	}

	public final int getMeshNum() {
		return meshList.size();
	}

	public final int getLightNum() {
		return meshLightList.size();
	}

	public final void setMaxBSPDivisionDepth(int bspDepth) {
		this.bspDepth = bspDepth;
	}

	public final int getMaxBSPDivisionDepth() {
		return bspDepth;
	}

	public final void setMaxTrianglesPerBSPNode(int maxTrianglePerNode) {
		this.maxTrianglesPerNode = maxTrianglePerNode;
	}

	public final int getMaxTrianglesPerBSPNode() {
		return maxTrianglesPerNode;
	}

	private final void genLightList() {
		meshLightList.clear();
		for (TriangleMesh t : meshList) {
			if (t.getBSDF().isLight()) {
				meshLightList.add(t);
			}
		}
	}

	boolean isVisible(Vector p0, Vector p1) {
		Ray ray = new Ray();
		ray.setLengthToMax();
		Vector d = new Vector();
		Vector.sub(p1, p0, d);
		d.normalize();
		ray.setDirection(d);
		ray.setOrigin(p0.x + d.x * MathTools.EPSILON, p0.y + d.y
				* MathTools.EPSILON, p0.z + d.z * MathTools.EPSILON);
		RayIntersectionInfo ir = new RayIntersectionInfo();
		bSPTree.intersect(ray, ir);
		return ir.isIntersected();
	}

	public final int getTrianglesNum() {
		return triangleList.size();
	}

	/**
	 * 
	 * @return
	 */
	public final Sky getSky() {
		return sky;
	}

	/**
	 * 
	 * @param ray
	 * @param dest
	 */
	public final void intersect(Ray ray, RayIntersectionInfo dest) {
		bSPTree.intersect(ray, dest);
	}

	public final void finish() {
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
		bSPTree = new KdTree(box, bspDepth, maxTrianglesPerNode);
	}

	@Override
	public Iterator<TriangleMesh> iterator() {
		// TODO Auto-generated method stub
		return meshList.iterator();
	}
}
