package prime.core;

import prime.math.Filter;
import prime.math.MathUtils;
import prime.math.Vec3f;
import prime.model.RayTriIntInfo;
import prime.model.Triangle;
import prime.model.TriangleMesh;
import prime.physics.Material;
import prime.physics.Ray;
import prime.physics.Color3f;

/**
 * Abstract Renderer
 */
public abstract class Renderer {
	protected Scene sceneGraph;
	protected int maxDepth;
	protected Color3f backgroundSpectrum;
	protected Camera camera;
	protected Filter filter;

	public Renderer() {
	}

	public Renderer(Scene sceneGraph) {
		this.sceneGraph = sceneGraph;
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}

	public void setBackgroundColor(Color3f backgroundColor) {
		this.backgroundSpectrum = backgroundColor;
	}

	public void setBouncingDepth(int maxDepth) {
		this.maxDepth = maxDepth;
	}

	public void setSceneGraph(Scene sceneGraph) {
		this.sceneGraph = sceneGraph;
	}

	/**
	 * compute the direct illumination
	 * 
	 * @param hitPoint
	 * @param normal
	 * @param bsdf
	 * @param destColor
	 */
	protected void directIllumination(Ray srcRay, Vec3f hitPoint,
			Vec3f normal, Material bsdf, Color3f destColor) {
		RayTriIntInfo ir = new RayTriIntInfo();
		Ray newRay = new Ray();
		Vec3f newDir = newRay.getDirection();
		Color3f spectrum = newRay.getSpectrum();

		//
		TriangleMesh meshLight;
		Triangle triangleLight;
		int nLights = sceneGraph.getLightNum();
		if (nLights > 0) {
			meshLight = sceneGraph.getLight((int) (Math.random() * nLights));
			meshLight.randomPoint(newDir);
			newDir.sub(hitPoint);
			newDir.normalize();
			newRay.setOrigin(hitPoint.x + MathUtils.EPSILON * newDir.x,
					hitPoint.y + MathUtils.EPSILON * newDir.y, 
					hitPoint.z + MathUtils.EPSILON * newDir.z);
			newRay.setLengthToMax();
			sceneGraph.intersect(newRay, ir);
			triangleLight = ir.getTriangle();
			float cos = Vec3f.dot(newDir, normal);
			if (ir.isHit()
					&& triangleLight.getTriangleMesh() == meshLight && cos > 0) {
				float u = ir.getU(), v = ir.getV();
				Vec3f normalLight = new Vec3f();
				normalLight = triangleLight.interpolateNormal(u, v);

				spectrum.set(meshLight.getMaterial().getEmittance());
				spectrum.multiply(cos
						* Math.abs(Vec3f.dot(newDir, normalLight)) * nLights);// *
																				// meshLight.getArea());
				spectrum.blend(bsdf.getReflectance());

				Color3f tmp = new Color3f();
				Vec3f srcDir = srcRay.getDirection();
				bsdf.brdf(hitPoint, normal, srcDir, newDir, tmp);
				spectrum.blend(tmp);
			}
			destColor.add(spectrum);
			destColor.add(bsdf.getEmittance());
		}

		// the sky illumination
		// Sky sky = sceneGraph.getSky();
		// newDir.set(sky.getDirection());
		// newDir.negate();
		// newRay.setOrigin(hitPoint.x + MathToolkit.EPSILON * newDir.x,
		// hitPoint.y + MathToolkit.EPSILON * newDir.y,
		// hitPoint.z + MathToolkit.EPSILON * newDir.z); //
		//
		// newRay.setLengthToMax();
		// sceneGraph.intersect(newRay, ir);
		// if (!ir.isIntersected()) {
		// destColor.add(sky.getSpectrum());
		// }
	}

	/**
	 * 
	 * @param filter
	 */
	public void setFilter(Filter filter) {
		this.filter = filter;
	}

	/**
	 * @return the filter
	 */
	public Filter getFilter() {
		return filter;
	}

	/**
     * 
     */
	public abstract void preprocess();

	/**
	 * 
	 * @param srcRay
	 */
	public abstract void render(Ray srcRay);
}
