/**
 * 
 */
package prime.physics;

import prime.math.Vector;

/**
 * @author Alan Liu
 *
 */
public final class Sky {
    private Spectrum spectrum = new Spectrum();
    private Vector direction = new Vector(0, -1, 0);
    
    public Sky() {
	
    }
    
    public Vector getDirection() {
	return direction;
    }
    
    public void setDirection(Vector direction) {
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
