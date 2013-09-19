/**
 * 
 */
package prime.physics;

import prime.math.Vec3;

/**
 * @author Alan Liu
 *
 */
public final class Sky {
    private Spectrum spectrum = new Spectrum();
    private Vec3 direction = new Vec3(0, -1, 0);
    
    public Sky() {
	
    }
    
    public Vec3 getDirection() {
	return direction;
    }
    
    public void setDirection(Vec3 direction) {
	this.direction.set(direction);
	this.direction.normalize();
    }
    
    public Spectrum getSpectrum() {
	return spectrum;
    }
    
    public void setSpectrum(Spectrum spectrum) {
	this.spectrum = spectrum;
    }
}
