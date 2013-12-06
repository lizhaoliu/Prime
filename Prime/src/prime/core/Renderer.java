package prime.core;

import prime.math.Filter;
import prime.math.MathUtils;
import prime.math.Vec3;
import prime.model.RayIntersectionInfo;
import prime.model.Triangle;
import prime.model.TriangleMesh;
import prime.physics.BSDF;
import prime.physics.Ray;
import prime.physics.Spectrum;

/**
 * provides an abstract of a renderer
 * @author lizhaoliu
 *
 */
public abstract class Renderer {
	protected SceneGraph sceneGraph;
	protected int maxDepth;
	protected Spectrum backgroundSpectrum;
	protected Camera camera;
	protected Filter filter;

	public Renderer() {
	}

	public Renderer(SceneGraph sceneGraph) {
		this.sceneGraph = sceneGraph;
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}

	public void setBackgroundColor(Spectrum backgroundColor) {
		this.backgroundSpectrum = backgroundColor;
	}

	public void setBouncingDepth(int maxDepth) {
		this.maxDepth = maxDepth;
	}

	public void setSceneGraph(SceneGraph sceneGraph) {
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
	protected void directIllumination(Ray srcRay, Vec3 hitPoint,
			Vec3 normal, BSDF bsdf, Spectrum destColor) {
		RayIntersectionInfo ir = new RayIntersectionInfo();
		Ray newRay = new Ray();
		Vec3 newDir = newRay.getDirection();
		Spectrum spectrum = newRay.getSpectrum();

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
			float cos = Vec3.dot(newDir, normal);
			if (ir.isIntersected()
					&& triangleLight.getTriangleMesh() == meshLight && cos > 0) {
				float u = ir.getU(), v = ir.getV();
				Vec3 normalLight = new Vec3();
				normalLight = triangleLight.interpolateNormal(u, v);

				spectrum.set(meshLight.getBSDF().getEmittance());
				spectrum.multiply(cos
						* Math.abs(Vec3.dot(newDir, normalLight)) * nLights);// *
																				// meshLight.getArea());
				spectrum.blend(bsdf.getReflectance());

				Spectrum tmp = new Spectrum();
				Vec3 srcDir = srcRay.getDirection();
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
	public abstract void prepareForRendering();

	/**
	 * 
	 * @param srcRay
	 */
	public abstract void render(Ray srcRay);
}
