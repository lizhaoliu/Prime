package prime.core;

//import org.llz.rt.photonmap.Photon;
import prime.math.MathTools;
import prime.math.Vector;
import prime.model.RayIntersectionInfo;
import prime.model.Triangle;
import prime.physics.BSDF;
import prime.physics.Ray;
import prime.physics.Spectrum;

/**
 * the path-tracing renderer
 * @author lizhaoliu
 *
 */
public final class PathTracer extends Renderer {
	// private Photon photon;

	public final void prepareForRendering() {
	}

	public final void render(Ray srcRay) {
		// tracePhoton(1);
		tracePath(srcRay, 1);
	}

	/**
	 * 
	 * @param srcRay
	 * @param depth
	 */
	private final void tracePath(Ray srcRay, int depth) {
		Spectrum destSpectrum = srcRay.getSpectrum();
		// �󽻲���
		srcRay.setLengthToMax();
		RayIntersectionInfo intResult = new RayIntersectionInfo();
		sceneGraph.intersect(srcRay, intResult);
		if (!intResult.isIntersected()) // �������û�к��κ������ཻ���򷵻�
		{
			destSpectrum.add(backgroundSpectrum);
			return;
		}

		// ����ཻ��������ǹ�Դ
		Triangle intTriangle = intResult.getTriangle();
		BSDF bsdf = intTriangle.getBSDF();
		if (bsdf.isLight()) {
			destSpectrum.add(bsdf.getEmittance());
			return;
		}

		// �����ֵ
		Ray newRay = new Ray();
		Vector newDir = new Vector();
		newRay.getDirection(newDir);
		float u = intResult.getU(), v = intResult.getV();
		Vector hitPoint = new Vector(), normal = new Vector();// , texCoord =
		// new
		// Vector3();
		Vector srcDir = new Vector();
		srcRay.getDirection(srcDir);
		intTriangle.interpolateVertex(u, v, hitPoint);
		intTriangle.interpolateNormal(u, v, normal);
		// intTriangle.interpolateTexCoord(u, v, texCoord);

		if (depth >= maxDepth) // ���ǰ��ȳ������׷����ȣ��˳�
		{
			// connect(hitPoint, normal, bsdf, destSpectrum);
			// directIllumination(srcRay, hitPoint, normal, bsdf, destSpectrum);
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

		directIllumination(srcRay, hitPoint, normal, bsdf, destSpectrum);

		// ��ݲ��ʲ�����������Ҫ�����������
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
		factor *= (float) (Math.abs(Vector.dot(normal, newDir)));
		newRay.setOrigin(hitPoint.x + MathTools.EPSILON * newDir.x, hitPoint.y
				+ MathTools.EPSILON * newDir.y, hitPoint.z + MathTools.EPSILON
				* newDir.z);
		newRay.setDirection(newDir);
		newRay.setLengthToMax();
		newRay.getSpectrum().zeroAll();
		tracePath(newRay, depth + 1);
		bsdf.brdf(hitPoint, normal, srcDir, newDir, tmpSpectrum);
		resSpectrum.blend(tmpSpectrum);
		resSpectrum.multiply(factor);
		destSpectrum.add(resSpectrum);
	}

	/**
	 * trace the photon emitting from light
	 * 
	 * @param ray
	 * @param depth
	 */
	// private final void tracePhoton(int depth)
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
	// //�н���
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
	// // if (Vector3.dot(normal, dir) < 0) //�������
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
	// private final void connect(Vector3 hitPoint, Vector3 normal, BSDF bsdf,
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
	public final String toString() {
		return "Path Tracer";
	}
}
