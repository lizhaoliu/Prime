package prime.physics;

import prime.math.Vec3f;

public abstract class SparrowTorranceModel extends Material {
    protected MicrofacetDistribution d;

    public void setMicrofacetDistribution(MicrofacetDistribution d) {
	this.d = d;
    }

    public MicrofacetDistribution getMicrofacetDistribution() {
	return d;
    }

    @Override
    public void brdf(Vec3f origin, Vec3f normal, Vec3f inDir,
	    Vec3f outDir, Color3f dest) {
	// TODO Auto-generated method stub
    }

    @Override
    public float samplingReflectionDirection(Vec3f origin, Vec3f normal,
	    Vec3f inDir, Vec3f dest) {
	// TODO Auto-generated method stub
	return 1.0f;
    }

    @Override
    public float samplingTransmissionDirection(Vec3f origin, Vec3f normal,
	    Vec3f inDir, Vec3f dest) {
	// TODO Auto-generated method stub
	return 1.0f;
    }

}
