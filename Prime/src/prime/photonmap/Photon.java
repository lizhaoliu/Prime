package prime.photonmap;

import java.io.Serializable;


import prime.math.Vector;
import prime.physics.BSDF;
import prime.physics.Spectrum;

/**
 * 
 * @author lizhaoliu
 * 
 */
public final class Photon implements Serializable {
    private static final long serialVersionUID = 8286259430083090915L;

    public Spectrum spectrum = new Spectrum();
    public Vector location = new Vector();
    public Vector normal = new Vector();
    public Vector inDir = new Vector();
    public BSDF bsdf;

    public Photon(Vector location, Vector normal, Vector inDir, BSDF bsdf,
	    Spectrum spectrum) {
	this.location.set(location);
	this.normal.set(normal);
	this.inDir.set(inDir);
	this.bsdf = bsdf;
	this.spectrum.set(spectrum);
    }

    public final float distance(Photon p) {
	return Vector.distance(location, p.location);
    }
}
