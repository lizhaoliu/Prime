package prime.core;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

/**
 * interface of all drawables
 * @author lizhaoliu
 *
 */
public interface Drawable {
	/**
	 * Draw wire model
	 * 
	 * @param g
	 * @param isSelected
	 * @param c
	 */
	public void draw(GL2 gl, GLU glu, Camera camera);
}
