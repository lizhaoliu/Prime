package prime.core;

import prime.math.Filter;
import prime.math.MathTools;
import prime.math.Vector;
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

	public final void setCamera(Camera camera) {
		this.camera = camera;
	}

	public final void setBackgroundColor(Spectrum backgroundColor) {
		this.backgroundSpectrum = backgroundColor;
	}

	public final void setBouncingDepth(int maxDepth) {
		this.maxDepth = maxDepth;
	}

	public final void setSceneGraph(SceneGraph sceneGraph) {
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
	protected final void directIllumination(Ray srcRay, Vector hitPoint,
			Vector normal, BSDF bsdf, Spectrum destColor) {
		RayIntersectionInfo ir = new RayIntersectionInfo();
		Ray newRay = new Ray();
		Vector newDir = new Vector();
		newRay.getDirection(newDir);
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
			newRay.setOrigin(hitPoint.x + MathTools.EPSILON * newDir.x,
					hitPoint.y + MathTools.EPSILON * newDir.y, hitPoint.z
							+ MathTools.EPSILON * newDir.z); //
			newRay.setLengthToMax();
			sceneGraph.intersect(newRay, ir);
			triangleLight = ir.getTriangle();
			float cos = Vector.dot(newDir, normal);
			if (ir.isIntersected()
					&& triangleLight.getTriangleMesh() == meshLight && cos > 0) {
				float u = ir.getU(), v = ir.getV();
				Vector normalLight = new Vector();
				triangleLight.interpolateNormal(u, v, normalLight);

				spectrum.set(meshLight.getBSDF().getEmittance());
				spectrum.multiply(cos
						* Math.abs(Vector.dot(newDir, normalLight)) * nLights);// *
																				// meshLight.getArea());
				spectrum.blend(bsdf.getReflectance());

				Spectrum tmp = new Spectrum();
				Vector srcDir = new Vector();
				srcRay.getDirection(srcDir);
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
