package prime.core;

import javax.annotation.Nonnull;

import prime.math.Filter;
import prime.math.MathUtils;
import prime.math.Vec3f;
import prime.model.RayTriHitInfo;
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
  protected Color3f backgroundColor;
  protected Camera camera;
  protected Filter filter;

  public Renderer() {}

  public void setCamera(@Nonnull Camera camera) {
    this.camera = camera;
  }

  public void setBackgroundColor(@Nonnull Color3f backgroundColor) {
    this.backgroundColor = backgroundColor;
  }

  public void setBouncingDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public void setSceneGraph(@Nonnull Scene sceneGraph) {
    this.sceneGraph = sceneGraph;
  }

  /**
   * compute the direct illumination
   * 
   * @param hitPoint
   * @param normal
   * @param material
   * @param destColor
   */
  protected void directIllumination(Ray srcRay, Vec3f hitPoint, Vec3f normal, Material material, Color3f destColor) {
    Ray newRay = new Ray();
    Vec3f newDir = newRay.getDirection();
    Color3f color = newRay.getColor();

    //
    TriangleMesh meshLight;
    Triangle triangleLight;
    int nLights = sceneGraph.getLightNum();
    if (nLights > 0) {
      meshLight = sceneGraph.getLight((int) (Math.random() * nLights));
      newDir = meshLight.randomPoint().sub(hitPoint).normalize();
      newRay.setOrigin(hitPoint.x + MathUtils.EPSILON * newDir.x, hitPoint.y + MathUtils.EPSILON * newDir.y, hitPoint.z
          + MathUtils.EPSILON * newDir.z);
      newRay.setLengthToMax();
      RayTriHitInfo ir = sceneGraph.intersect(newRay);
      triangleLight = ir.getHitTriangle();
      float cos = MathUtils.dot(newDir, normal);
      if (ir.isHit() && triangleLight.getTriangleMesh() == meshLight && cos > 0) {
        float u = ir.getU(), v = ir.getV();
        Vec3f normalLight = new Vec3f();
        normalLight = triangleLight.interpolateNormal(u, v);

        color.set(meshLight.getMaterial().getEmittance());
        color.multiply(cos * Math.abs(MathUtils.dot(newDir, normalLight)) * nLights);// *
        // meshLight.getArea());
        color.blend(material.getReflectance());

        Color3f tmp = new Color3f();
        Vec3f srcDir = srcRay.getDirection();
        material.brdf(hitPoint, normal, srcDir, newDir, tmp);
        color.blend(tmp);
      }
      destColor.add(color);
      destColor.add(material.getEmittance());
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
