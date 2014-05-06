/**
 * 
 */
package prime.physics;

import prime.math.Vec3f;

/**
 * @author Alan Liu
 * 
 */
public final class Sky {
  private Color3f spectrum = new Color3f();
  private Vec3f direction = new Vec3f(0, -1, 0);

  public Sky() {}

  public Vec3f getDirection() {
    return direction;
  }

  public void setDirection(Vec3f direction) {
    this.direction.set(direction);
    this.direction.normalize();
  }

  public Color3f getSpectrum() {
    return spectrum;
  }

  public void setSpectrum(Color3f spectrum) {
    this.spectrum = spectrum;
  }
}
