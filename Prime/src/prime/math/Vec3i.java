package prime.math;

/**
 *
 */
public class Vec3i {
  public final int x, y, z;

  /**
   * @param i0
   * @param i1
   * @param i2
   */
  public Vec3i(int i0, int i1, int i2) {
    this.x = i0;
    this.y = i1;
    this.z = i2;
  }

  @Override
  public String toString() {
    return "Vec3i [x=" + x + ", y=" + y + ", z=" + z + "]";
  }

  /**
   * @param i
   * @return
   */
  public int get(int i) {
    switch (i) {
      case 0:
        return x;

      case 1:
        return y;

      default:
        return z;
    }
  }
}
