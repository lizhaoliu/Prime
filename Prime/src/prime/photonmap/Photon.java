package prime.photonmap;

import prime.math.Vec3f;
import prime.physics.Color3f;
import prime.physics.Material;

import java.io.Serializable;

import static prime.math.MathUtils.dist;

/**
 * @author lizhaoliu
 */
public class Photon implements Serializable {
  private static final long serialVersionUID = 8286259430083090915L;

  public Color3f spectrum = new Color3f();
  public Vec3f location = new Vec3f();
  public Vec3f normal = new Vec3f();
  public Vec3f inDir = new Vec3f();
  public Material bsdf;

  public Photon(Vec3f location, Vec3f normal, Vec3f inDir, Material bsdf, Color3f spectrum) {
    this.location.set(location);
    this.normal.set(normal);
    this.inDir.set(inDir);
    this.bsdf = bsdf;
    this.spectrum.set(spectrum);
  }

  public float distance(Photon p) {
    return dist(location, p.location);
  }
}
