package prime.core;

/**
 *  
 */
public interface Sampler {
	
	/**
	 * Get the i-th sample, ranged [0, 1]
	 * @param i
	 * @return
	 */
	float getSample(int i);
}
