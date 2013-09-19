package prime.physics;

import prime.math.Vec3;

public abstract class MicrofacetDistribution {
    public abstract float d(Vec3 normal, Vec3 inDir, Vec3 outDir);
}
