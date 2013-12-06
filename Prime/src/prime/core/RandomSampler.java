package prime.core;

import prime.math.MathUtils;

public class RandomSampler implements Sampler {

	@Override
	public float getSample(int i) {
		return MathUtils.random();
	}

}
