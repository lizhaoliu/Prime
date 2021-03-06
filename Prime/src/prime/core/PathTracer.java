package prime.core;

import prime.math.MathUtils;
import prime.math.Vec3f;
import prime.model.RayTriHitInfo;
import prime.model.Triangle;
import prime.physics.Color3f;
import prime.physics.Material;
import prime.physics.Ray;

import static prime.math.MathUtils.EPSILON;
import static prime.math.MathUtils.dot;

/**
 * Path tracing renderer
 */
public class PathTracer extends Renderer {

  public void preprocess() {
  }

  public void render(Ray ray) {
    render(ray, 1);
  }

  /**
   * @param srcRay
   * @param depth
   */
  private void render(Ray srcRay, int depth) {
    Color3f finalColor = srcRay.getColor();

    srcRay.setLengthToMax();
    RayTriHitInfo hitRes = sceneGraph.intersect(srcRay);
    if (!hitRes.isHit()) {
      finalColor.add(backgroundColor);
      return;
    }

    Triangle intTriangle = hitRes.getHitTriangle();
    Material material = intTriangle.getMaterial();
    if (material.isLight()) {
      finalColor.add(material.getEmittance());
      return;
    }

    float u = hitRes.getU(), v = hitRes.getV();
    Vec3f hitPoint = new Vec3f(), normal = new Vec3f();// , texCoord =
    // new
    // Vector3();
    Vec3f srcDir = srcRay.getDirection();
    hitPoint = intTriangle.interpolatePosition(u, v);
    normal = intTriangle.interpolateNormal(u, v);
    // intTriangle.interpolateTexCoord(u, v, texCoord);
    Ray newRay = new Ray();
    Vec3f newDir = newRay.getDirection();

    if (depth >= maxDepth) //
    {
      // connect(hitPoint, normal, bsdf, destSpectrum);
      directIllumination(srcRay, hitPoint, normal, material, finalColor);
      return;
    }

    Color3f reflectance, transmission, absorption;
    float refAvg, transAvg, abspAvg;

    reflectance = material.getReflectance();
    transmission = material.getTransmission();
    absorption = material.getAbsorption();

    refAvg = reflectance.average();
    transAvg = transmission.average();
    abspAvg = absorption.average();

    Color3f resColor = newRay.getColor();
    Color3f tmpColor = new Color3f();

    directIllumination(srcRay, hitPoint, normal, material, finalColor);

    // perform a Russian Roulette
    float roulette = MathUtils.rand() * (refAvg + transAvg + abspAvg);
    float factor;
    if (roulette < refAvg) // reflection
    {
      Material.Sample sample = material.samplingReflectionDirection(hitPoint, normal, srcDir);
      newDir = sample.outDir;
      factor = 1.0f / sample.prob;
      factor /= refAvg;
    } else if (roulette >= refAvg && roulette < (refAvg + transAvg)) // transmit
    {
      Material.Sample sample = material.samplingTransmissionDirection(hitPoint, normal, srcDir);
      newDir = sample.outDir;
      factor = 1.0f / sample.prob;
      factor /= transAvg;
    } else {
      // connect(hitPoint, normal, bsdf, destSpectrum);
      // directIllumination(srcRay, hitPoint, normal, bsdf, destSpectrum);
      return;
    }
    factor *= Math.abs(dot(normal, newDir));
    newRay.setOrigin(new Vec3f(
        hitPoint.x + EPSILON * newDir.x,
        hitPoint.y + EPSILON * newDir.y,
        hitPoint.z + EPSILON * newDir.z));
    newRay.setDirection(newDir);
    newRay.setLengthToMax();
    newRay.getColor().zeroAll();
    render(newRay, depth + 1);
    material.brdf(hitPoint, normal, srcDir, newDir, tmpColor);
    resColor.blend(tmpColor);
    resColor.multiply(factor);
    finalColor.add(resColor);
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
  // // if (Vector3.dot(normal, dir) < 0)
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
