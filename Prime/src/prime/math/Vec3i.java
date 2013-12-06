package prime.math;

/**
 * 
 */
public class Vec3i {
	public int i0, i1, i2;

	/**
	 * 
	 * @param i0
	 * @param i1
	 * @param i2
	 */
	public Vec3i(int i0, int i1, int i2) {
		this.i0 = i0;
		this.i1 = i1;
		this.i2 = i2;
	}

	/**
	 * 
	 * @param i
	 * @return
	 */
	public int get(int i) {
		switch (i) {
		case 0:
			return i0;

		case 1:
			return i1;

		default:
			return i2;
		}
	}

	/**
	 * 
	 * @param i
	 * @param val
	 */
	public void set(int i, int val) {
		switch (i) {
		case 0:
			i0 = val;
			break;

		case 1:
			i1 = val;
			break;

		default:
			i2 = val;
			break;
		}
	}
}
