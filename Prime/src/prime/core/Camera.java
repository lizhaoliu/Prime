package prime.core;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Observable;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

import org.apache.commons.lang.math.RandomUtils;

import com.google.common.base.Preconditions;

import prime.math.LHCoordinateSystem;
import prime.math.Transformable;
import prime.math.Vec3f;
import prime.model.RayTriHitInfo;
import prime.model.TriangleMesh;
import prime.physics.Color3f;
import prime.physics.Ray;

/**
 * 
 */
public class Camera extends Observable implements Serializable, Transformable, Drawable {
  private static final long serialVersionUID = -4797769528520234991L;

  private int nSamples;

  private int width;
  private int height;
  private float aspectRatio;

  private float minX;
  private float minY;
  private float dX;
  private float dY;
  private float zNear;
  private float zFar;

  private LHCoordinateSystem coordSys;
  private Scene sceneGraph;
  private Color3f backgroundColor;
  private Vec3f origin = new Vec3f(0f, 0f, 0f);

  private transient volatile boolean isStopRequested = false;

  private transient Renderer renderer;

  /**
   * 
   * @param width
   * @param height
   */
  public Camera(int width, int height) {
    setScreenSize(width, height);
    nSamples = 1;
    zNear = -0.5f;
    zFar = -1000;
    coordSys = new LHCoordinateSystem();
    setViewportMap(-5, -5, 5, 5);
    backgroundColor = new Color3f();
  }

  public LHCoordinateSystem getCoordinateSystem() {
    return coordSys;
  }

  public void setRenderer(Renderer renderer) {
    this.renderer = renderer;
    this.renderer.setSceneGraph(sceneGraph);
    this.renderer.setBackgroundColor(backgroundColor);
    this.renderer.setCamera(this);
  }

  public float getLeft() {
    return minX;
  }

  public float getRight() {
    return minX + dX;
  }

  public float getTop() {
    return minY + dY;
  }

  public float getBottom() {
    return minY;
  }

  public float getZNear() {
    return zNear;
  }

  public float getZFar() {
    return zFar;
  }

  /**
	 * 
	 */
  public void stopRendering() {
    isStopRequested = true;
  }

  /**
   * 
   * @param backgroundColor
   */
  public void setBackgroundColor(Color3f backgroundColor) {
    this.backgroundColor.set(backgroundColor);
  }

  /**
   *  
   * @return
   */
  public Color3f getBackgoundColor() {
    return backgroundColor;
  }

  /**
   * 
   * @param eye
   * @param focus
   * @param up
   */
  public void lookAt(Vec3f eye, Vec3f focus, Vec3f up) {
    Vec3f d = new Vec3f(focus);
    d.sub(eye);
    d.normalize();
    d.negate();
    up.normalize();
    Vec3f newX = Vec3f.cross(up, d);
    coordSys.setParentToLocalMatrix(newX.x, up.x, d.x, newX.y, up.y, d.y, newX.z, up.z, d.z);
    coordSys.setOrigin(eye);
  }

  /**
	 * 
	 */
  public void translate(Vec3f displacement) {
    coordSys.translateInLocal(displacement);
  }

  /**
   * 
   * @param p
   * @param dest
   * @return
   */
  public Vec3f transPointToLocal(Vec3f p) {
    return coordSys.transPointToLocal(p);
  }

  /**
   * 
   * @param v
   * @param dest
   * @return
   */
  public Vec3f transVectorToLocal(Vec3f v) {
    return coordSys.transVectorToLocal(v);
  }

  /**
	 * 
	 */
  public void rotate(Vec3f axis, float angle) {
    coordSys.rotate(axis, angle);
  }

  /**
   * 
   * @param width
   * @param height
   */
  public void setScreenSize(int width, int height) {
    this.width = width;
    this.height = height;
    this.aspectRatio = (float) width / height;
  }

  /**
   * 
   * @param n
   */
  public void setSamplesPerPixel(int n) {
    nSamples = (n < 1 ? 1 : n);
  }

  /**
   * 
   * @param s
   */
  public void setScene(@Nonnull Scene s) {
    Preconditions.checkNotNull(s);
    
    sceneGraph = s;
  }

  /**
   * 
   * @param minX
   * @param minY
   * @param maxX
   * @param maxY
   */
  public void setViewportMap(float minX, float minY, float maxX, float maxY) {
    dX = maxX - minX;
    dY = maxY - minY;
    this.minX = minX;
    this.minY = minY;
  }

  /**
   * 
   * @param zNear
   */
  public void setZNear(float zNear) {
    this.zNear = -Math.abs(zNear);
  }

  /**
   * 
   * @param zFar
   */
  public void setZFar(float zFar) {
    this.zFar = -Math.abs(zFar);
  }

  /**
   * 
   * @param x
   * @param y
   * @return
   */
  public Vec3f getLocalPointFromScreen(float x, float y) {
    if (aspectRatio >= 1) {
      return new Vec3f((x / width * dX + minX) * aspectRatio, (minY + (1 - y / height) * dY), zNear);
    } else {
      return new Vec3f((x / width * dX + minX), (minY + (1 - y / height) * dY) / aspectRatio, zNear);
    }
  }

  /**
   * 
   * @param x
   * @param y
   * @return
   */
  public Vec3f getWorldPointFromScreen(float x, float y) {
    Vec3f v = getLocalPointFromScreen(x, y);
    return coordSys.transPointToParent(new Vec3f((v.x), (v.y), zNear));
  }

  /**
   * 
   * @param p
   * @return
   */
  public int getXFromWorld(Vec3f p) {
    float px = p.x, py = p.y, pz = p.z;
    p = coordSys.transPointToLocal(p);
    float x = p.x * zNear / (zNear + p.z);
    p.set(px, py, pz);
    return getXFromViewport(x);
  }

  /**
   * 
   * @param p
   * @return
   */
  public int getYFromWorld(Vec3f p) {
    float px = p.x, py = p.y, pz = p.z;
    p = coordSys.transPointToLocal(p);
    float y = p.y * zNear / (zNear + p.z);
    p.set(px, py, pz);
    return getYFromViewport(y);
  }

  /**
   * 
   * @param p
   * @return
   */
  public int getXFromLocal(Vec3f p) {
    float x = p.x * zNear / (zNear + p.z);
    return getXFromViewport(x);
  }

  /**
   * 
   * @param p
   * @return
   */
  public int getYFromLocal(Vec3f p) {
    float y = p.y * zNear / (zNear + p.z);
    return getYFromViewport(y);
  }

  /**
   * Return the screen coordinate from view port
   * 
   * @param x
   * @return
   */
  public int getXFromViewport(float x) {
    if (aspectRatio >= 1) {
      x /= aspectRatio;
    }
    return (int) ((x - minX) * width / dX);
  }

  /**
   * Return the screen coordinate from view port
   * 
   * @param y
   * @return
   */
  public int getYFromViewport(float y) {
    if (aspectRatio < 1) {
      y *= aspectRatio;
    }
    return (int) ((minY + dY - y) * height / dY);
  }

  /**
   * 
   * @return
   */
  public int getWidth() {
    return width;
  }

  /**
   * 
   * @return
   */
  public int getHeight() {
    return height;
  }

  /**
   * 
   * @return
   */
  public Scene getSceneGraph() {
    return sceneGraph;
  }

  /**
   * 
   * @return
   */
  public int getSampleNumPerPixel() {
    return nSamples;
  }

  /**
   * Start rendering task in blocking mode
   * 
   * @param img
   * @param panel
   */
  public void renderSync(BufferedImage img, Component panel) {
    sceneGraph.finish();

    renderer.preprocess();
    isStopRequested = false;

    int nThreads = Runtime.getRuntime().availableProcessors();
    Collection<RenderTask> tasks = new ArrayList<Camera.RenderTask>(nThreads);
    for (int i = 0; i < nThreads; i++) {
      tasks.add(new RenderTask(nThreads, i, img, panel));
    }
    try {
      List<Future<Void>> futureList = Executors.newFixedThreadPool(nThreads).invokeAll(tasks);
      for (Future<Void> future : futureList) {
        future.get();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * 
   * @param ray
   */
  private void render(Ray ray) {
    renderer.render(ray);
  }

  /**
   * 
   * @param x
   * @param y
   * @param xOffset
   * @param yOffset
   * @param dest
   * @return
   */
  public Ray getRayFromViewport(int x, int y, float xOffset, float yOffset, @Nullable Ray dest) {
    Vec3f o = new Vec3f(), d = getLocalPointFromScreen(x + xOffset, y + yOffset);
    d.normalize();
    o = coordSys.transPointToParent(origin);
    d = coordSys.transVectorToParent(d);
    dest.setOrigin(o);
    dest.setDirection(d);
    dest.setLengthToMax();
    return dest;
  }

  /**
   * 
   * @param gl
   * @param glu
   */
  public void drawScene(GL2 gl, GLU glu) {
    for (int i = 0; i < sceneGraph.getMeshNum(); i++) {
      sceneGraph.getMesh(i).draw(gl, glu, this);
    }
  }

  /**
   * 
   */
  private class RenderTask implements Callable<Void> {
    private int nThreads;
    private int iThread;
    private BufferedImage img;
    private Component panel;

    public RenderTask(int nThreads, int iThread, BufferedImage img, Component panel) {
      this.nThreads = nThreads;
      this.iThread = iThread;
      this.img = img;
      this.panel = panel;
    }

    public Void call() {
      Ray ray = new Ray();

      float[][] xJittered = new float[nSamples][nSamples], yJittered = new float[nSamples][nSamples];
      for (int i = 0; i < nSamples; i++) {
        for (int j = 0; j < nSamples; j++) {
          xJittered[i][j] = (i + RandomUtils.nextFloat()) / nSamples;
          yJittered[i][j] = (j + RandomUtils.nextFloat()) / nSamples;
        }
      }

      float scale = 1f / (nSamples * nSamples);
      int x = iThread, y = 0;
      while (y < height) {
        if (isStopRequested) {
          return null;
        }

        // sample all
        Color3f rayColor = ray.getColor();
        rayColor.zeroAll();
        for (int i = 0; i < nSamples; i++) {
          for (int j = 0; j < nSamples; j++) {
            getRayFromViewport(x, y, xJittered[i][j], yJittered[i][j], ray);
            render(ray);
          }
        }

        rayColor.multiply(scale);
        int argb = rayColor.toRgb();
        img.setRGB(x, y, argb);

        x += nThreads;
        if (x >= width) {
          x -= width;
          ++y;
          panel.repaint();
        }
      }

      return null;
    }
  }

  /**
   * Pick a {@link TriangleMesh} from screen
   * 
   * @param x
   * @param y
   * @return null if nothing is picked, or a {@link TriangleMesh}
   */
  @Nullable
  public TriangleMesh pick(int x, int y) {
    if (sceneGraph == null) {
      return null;
    }
    Ray ray = new Ray();
    getRayFromViewport(x, y, 0, 0, ray);
    RayTriHitInfo ir = sceneGraph.intersect(ray);
    if (ir.isHit()) {
      return ir.getHitTriangle().getTriangleMesh();
    }
    return null;
  }

  @Override
  public void draw(GL2 gl, GLU glu, Camera camera) {}
}
