package prime.physics;

import prime.math.Vector;

public abstract class MicrofacetDistribution {
    public abstract float d(Vector normal, Vector inDir, Vector outDir);
}
