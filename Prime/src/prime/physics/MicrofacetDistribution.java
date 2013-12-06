package prime.physics;

import prime.math.Vec3f;

public abstract class MicrofacetDistribution {
    public abstract float d(Vec3f normal, Vec3f inDir, Vec3f outDir);
}
