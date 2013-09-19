package prime.math;


/**
 * a cone filter
 * @author lizhaoliu
 *
 */
public class ConeFilter implements Filter {
	private float k = 1;

	public ConeFilter(float k) {
		this.k = k;
	}

	public float filter(float d, float r) {
		return (1 - d / (k * r)) / (1 - 2 / (3 * k));
	}

	public String toString() {
		return "Cone Filter";
	}
}
