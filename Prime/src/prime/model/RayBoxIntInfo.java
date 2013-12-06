package prime.model;

import prime.physics.Ray;

/**
 * {@link Ray} and {@link BoundingBox} intersection test result
 */
public final class RayBoxIntInfo {
	private final boolean isHit;
	private final float min;
	private final float max;
	
	public boolean isHit() {
		return isHit;
	}

	public float getMin() {
		return min;
	}

	public float getMax() {
		return max;
	}

	public RayBoxIntInfo(boolean isHit, float min, float max) {
		// TODO Auto-generated constructor stub
		this.isHit = isHit;
		this.min = min;
		this.max = max;
	}
}
