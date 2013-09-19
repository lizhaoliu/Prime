package prime.physics;

import prime.math.Vec3;

public abstract class SparrowTorranceModel extends BSDF {
    protected MicrofacetDistribution d;

    public void setMicrofacetDistribution(MicrofacetDistribution d) {
	this.d = d;
    }

    public MicrofacetDistribution getMicrofacetDistribution() {
	return d;
    }

    @Override
    public void brdf(Vec3 origin, Vec3 normal, Vec3 inDir,
	    Vec3 outDir, Spectrum dest) {
	// TODO Auto-generated method stub
    }

    @Override
    public float samplingReflectionDirection(Vec3 origin, Vec3 normal,
	    Vec3 inDir, Vec3 dest) {
	// TODO Auto-generated method stub
	return 1.0f;
    }

    @Override
    public float samplingTransmissionDirection(Vec3 origin, Vec3 normal,
	    Vec3 inDir, Vec3 dest) {
	// TODO Auto-generated method stub
	return 1.0f;
    }

}
