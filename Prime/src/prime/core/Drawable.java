package prime.core;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

/**
 * Interface that describes any object that can be drawn with Jogl context
 */
public interface Drawable {
  
  /**
   * 
   * 
   * @param gl
   * @param glu
   * @param camera
   */
  public void draw(GL2 gl, GLU glu, Camera camera);
}
