package prime.math;

/**
 * The Gaussian filter
 */
public class GaussianFilter implements Filter {
  private float alpha = 0.918f, beta = 1.953f;
  private float coefficient = 1f / (float) (1 - Math.exp(-beta));

  public GaussianFilter() {}

  @Override
  public float filter(float d, float r) {
    return (float) (alpha * (1 - ((1 - Math.exp(-beta * d * d / (2 * r * r)))) * coefficient));
  }

  public String toString() {
    return "Gaussian Filter";
  }
}
