package prime.core;

import prime.math.MathUtils;

public class StratifiedSampler implements Sampler {
	
	private final float[] grids;

	/**
	 * 
	 * @param numGrids
	 */
	public StratifiedSampler(int numGrids) {
		grids = new float[numGrids];
		for (int i = 0; i < grids.length; ++i) {
			grids[i] = (i + MathUtils.random()) / numGrids;
		}
	}
	
	@Override
	public float getSample(int i) {
		return grids[i % grids.length];
	}
}
