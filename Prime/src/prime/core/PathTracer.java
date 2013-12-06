package prime.core;

//import org.llz.rt.photonmap.Photon;
import prime.math.MathUtils;
import prime.math.Vec3;
import prime.model.RayTriIntInfo;
import prime.model.Triangle;
import prime.physics.BSDF;
import prime.physics.Ray;
import prime.physics.Spectrum;

/**
 * the path-tracing renderer
 * @author lizhaoliu
 *
 */
public class PathTracer extends Renderer {
	// private Photon photon;

	public void prepareForRendering() {
	}

	public void render(Ray srcRay) {
		// tracePhoton(1);
		tracePath(srcRay, 1);
	}

	/**
	 * 
	 * @param srcRay
	 * @param depth
	 */
	private void tracePath(Ray srcRay, int depth) {
		Spectrum dstSpectrum = srcRay.getSpectrum();
		//
		srcRay.setLengthToMax();
		RayTriIntInfo intRes = new RayTriIntInfo();
		sceneGraph.intersect(srcRay, intRes);
		if (!intRes.isHit())
		{
			dstSpectrum.add(backgroundSpectrum);
			return;
		}

		Triangle intTriangle = intRes.getTriangle();
		BSDF bsdf = intTriangle.getBSDF();
		if (bsdf.isLight()) {
			dstSpectrum.add(bsdf.getEmittance());
			return;
		}

		Ray newRay = new Ray();
		Vec3 newDir = newRay.getDirection();
		float u = intRes.getU(), v = intRes.getV();
		Vec3 hitPoint = new Vec3(), normal = new Vec3();// , texCoord =
		// new
		// Vector3();
		Vec3 srcDir = srcRay.getDirection();
		hitPoint = intTriangle.interpolateVertex(u, v);
		normal = intTriangle.interpolateNormal(u, v);
		// intTriangle.interpolateTexCoord(u, v, texCoord);

		if (depth >= maxDepth)	//
		{
			// connect(hitPoint, normal, bsdf, destSpectrum);
			 directIllumination(srcRay, hitPoint, normal, bsdf, dstSpectrum);
			return;
		}

		Spectrum reflectance, transmission, absorption;
		float refAvg, transAvg, abspAvg;

		reflectance = bsdf.getReflectance();
		transmission = bsdf.getTransmission();
		absorption = bsdf.getAbsorption();

		refAvg = reflectance.average();
		transAvg = transmission.average();
		abspAvg = absorption.average();

		Spectrum resSpectrum = newRay.getSpectrum();
		Spectrum tmpSpectrum = new Spectrum();

		directIllumination(srcRay, hitPoint, normal, bsdf, dstSpectrum);

		//
		float roulette = (float) (Math.random() * (refAvg + transAvg + abspAvg));
		float factor;
		if (roulette < refAvg) // reflection
		{
			factor = 1.0f / bsdf.samplingReflectionDirection(hitPoint, normal,
					srcDir, newDir);
			factor /= refAvg;
		} else if (roulette >= refAvg && roulette < (refAvg + transAvg)) // transmit
		{
			factor = 1.0f / bsdf.samplingTransmissionDirection(hitPoint,
					normal, srcDir, newDir);
			factor /= transAvg;
		} else {
			// connect(hitPoint, normal, bsdf, destSpectrum);
			// directIllumination(srcRay, hitPoint, normal, bsdf, destSpectrum);
			return;
		}
		factor *= (float) (Math.abs(Vec3.dot(normal, newDir)));
		newRay.setOrigin(hitPoint.x + MathUtils.EPSILON * newDir.x, 
				hitPoint.y + MathUtils.EPSILON * newDir.y, 
				hitPoint.z + MathUtils.EPSILON * newDir.z);
		newRay.setDirection(newDir);
		newRay.setLengthToMax();
		newRay.getSpectrum().zeroAll();
		tracePath(newRay, depth + 1);
		bsdf.brdf(hitPoint, normal, srcDir, newDir, tmpSpectrum);
		resSpectrum.blend(tmpSpectrum);
		resSpectrum.multiply(factor);
		dstSpectrum.add(resSpectrum);
	}

	/**
	 * trace the photon emitting from light
	 * 
	 * @param ray
	 * @param depth
	 */
	// private void tracePhoton(int depth)
	// {
	// int nLightMesh = sceneGraph.getLightNum();
	// int i = (int)(Math.random() * nLightMesh);
	// TriangleMesh lightMesh = sceneGraph.getLight(i);
	// Ray ray = new Ray();
	// lightMesh.emitRandomRay(ray);
	// float powFactor = 2;
	// ray.getSpectrum().multiply(nLightMesh * powFactor);
	//
	// IntersectionResult intResult = new IntersectionResult();
	// Triangle t;
	// float u, v;
	// BSDF bsdf = lightMesh.getBSDF();
	// float roulette;
	// Vector3 dir = new Vector3(), normal = new Vector3(), hitPoint = new
	// Vector3(), newDir = new Vector3();
	// ray.getDirection(dir);
	// Spectrum reflectance, transmission, absorption, srcSpectrum =
	// ray.getSpectrum();
	// float refAvg, transAvg, abspAvg;
	//
	// while (depth <= maxDepth)
	// {
	// sceneGraph.intersect(ray, intResult);
	//
	// if (!intResult.isIntersected())
	// {
	// if (depth == 1)
	// {
	// hitPoint.set(ray.getOrigin());
	// normal.set(ray.getDirection());
	// dir.set(ray.getDirection());
	// }
	// break;
	// }
	//
	// //锟叫斤拷锟斤拷
	// t = intResult.getTriangle();
	// u = intResult.getU();
	// v = intResult.getV();
	//
	// dir = ray.getDirection();
	// t.interpolateNormal(u, v, normal);
	// t.interpolateVertex(u, v, hitPoint);
	//
	// bsdf = t.getBSDF();
	// reflectance = bsdf.getReflectance();
	// transmission = bsdf.getTransmission();
	// absorption = bsdf.getAbsorption();
	//
	// refAvg = reflectance.average();
	// transAvg = transmission.average();
	// abspAvg = absorption.average();
	//
	// srcSpectrum = ray.getSpectrum();
	//
	// float factor = 1.0f;
	// roulette = (float)(Math.random() * (refAvg + transAvg + abspAvg));
	// if (roulette < refAvg) //reflection
	// {
	// srcSpectrum.divide(refAvg);
	// factor = 1.0f / bsdf.samplingReflectionDirection(hitPoint, normal, dir,
	// newDir);
	// bsdf.brdf(hitPoint, normal, dir, newDir, srcSpectrum);
	// srcSpectrum.multiply(Math.abs(Vector3.dot(dir, normal)));
	// }
	// else if (roulette >= refAvg && roulette < (refAvg + transAvg)) //transmit
	// {
	// // float refraT = 1f;
	// // if (Vector3.dot(normal, dir) < 0) //锟斤拷锟斤拷锟斤拷锟�
	// // {
	// // refraT = bsdf.getRefractiveIndex();
	// // }
	// // srcSpectrum.blend(transmission);
	// // MathToolkit.refractDirection(dir, normal, ray.getRefractiveIndex(),
	// refraT, dir);
	// // ray.setRefrativeIndex(refraT);
	// }
	// else //absorption
	// {
	// break;
	// }
	// srcSpectrum.multiply(factor);
	// ray.setOrigin(hitPoint.x + MathToolkit.EPSILON * newDir.x,
	// hitPoint.y + MathToolkit.EPSILON * newDir.y,
	// hitPoint.z + MathToolkit.EPSILON * newDir.z);
	// ray.setLengthToMax();
	//
	// ++depth;
	// }
	//
	// photon = new Photon(hitPoint, normal, dir, bsdf, srcSpectrum);
	// }
	//
	// private void connect(Vector3 hitPoint, Vector3 normal, BSDF bsdf,
	// Spectrum destSpectrum)
	// {
	// if (sceneGraph.isVisible(hitPoint, photon.location))
	// {
	// Vector3 outDir = new Vector3();
	// Vector3.sub(hitPoint, photon.location, outDir);
	// Spectrum s = new Spectrum();
	// photon.bsdf.brdf(photon.location, photon.normal, photon.inDir, outDir,
	// s);
	// float dot = (float)(Math.abs(Vector3.dot(normal, outDir)));
	// s.multiply(dot);
	// destSpectrum.blend(s);
	// }
	// }
	//
	@Override
	public String toString() {
		return "Path Tracer";
	}
}
