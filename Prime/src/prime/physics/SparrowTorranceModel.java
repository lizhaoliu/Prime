package prime.physics;

import prime.math.Vector;

public abstract class SparrowTorranceModel extends BSDF {
    protected MicrofacetDistribution d;

    public final void setMicrofacetDistribution(MicrofacetDistribution d) {
	this.d = d;
    }

    public final MicrofacetDistribution getMicrofacetDistribution() {
	return d;
    }

    @Override
    public void brdf(Vector origin, Vector normal, Vector inDir,
	    Vector outDir, Spectrum dest) {
	// TODO Auto-generated method stub
    }

    @Override
    public float samplingReflectionDirection(Vector origin, Vector normal,
	    Vector inDir, Vector dest) {
	// TODO Auto-generated method stub
	return 1.0f;
    }

    @Override
    public float samplingTransmissionDirection(Vector origin, Vector normal,
	    Vector inDir, Vector dest) {
	// TODO Auto-generated method stub
	return 1.0f;
    }

}
