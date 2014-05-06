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
  private Color3f color = new Color3f();
  private Vec3f direction = new Vec3f(0, -1, 0);

  public Sky() {}

  public Vec3f getDirection() {
    return direction;
  }

  public void setDirection(Vec3f direction) {
    this.direction.set(direction);
    this.direction.normalize();
  }

  public Color3f getColor() {
    return color;
  }

  public void setColor(Color3f color) {
    this.color = color;
  }
}
