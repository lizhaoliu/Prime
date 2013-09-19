package prime.photonmap;

import java.io.Serializable;


import prime.math.Vec3;
import prime.physics.BSDF;
import prime.physics.Spectrum;

/**
 * 
 * @author lizhaoliu
 * 
 */
public class Photon implements Serializable {
    private static final long serialVersionUID = 8286259430083090915L;

    public Spectrum spectrum = new Spectrum();
    public Vec3 location = new Vec3();
    public Vec3 normal = new Vec3();
    public Vec3 inDir = new Vec3();
    public BSDF bsdf;

    public Photon(Vec3 location, Vec3 normal, Vec3 inDir, BSDF bsdf,
	    Spectrum spectrum) {
	this.location.set(location);
	this.normal.set(normal);
	this.inDir.set(inDir);
	this.bsdf = bsdf;
	this.spectrum.set(spectrum);
    }

    public float distance(Photon p) {
	return Vec3.distance(location, p.location);
    }
}
