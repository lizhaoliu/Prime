package prime.core;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.Observable;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

import prime.math.LHCoordinateSystem;
import prime.math.Transformable;
import prime.math.Vector;
import prime.model.RayIntersectionInfo;
import prime.model.TriangleMesh;
import prime.physics.Ray;
import prime.physics.Spectrum;

/**
 * 
 * @author lizhaoliu
 *
 */
public final class Camera extends Observable implements Serializable,
		Transformable, Drawable {
	private static final long serialVersionUID = -4797769528520234991L;

	private int width; 
	private int height; //
	private int nSamples; 
	private float minX; 
	private float maxY;
	private float dX; 
	private float dY; 
	private float zNear; //
	private float zFar; //
	private float lens; //
	private LHCoordinateSystem coordSys; //
	private SceneGraph sceneGraph; 
	private Spectrum backgroundColor; 
	private Vector origin = new Vector(0f, 0f, 0f);

	private transient boolean isRenderingStopped = false;

	private transient Renderer renderer;

	/**
	 * 
	 * @param width
	 * @param height
	 */
	public Camera(int width, int height) {
		setViewportSize(width, height);
		nSamples = 1;
		zNear = -0.5f;
		zFar = -1000;
		coordSys = new LHCoordinateSystem();
		setViewportMap(-5, -5, 5, 5);
		backgroundColor = new Spectrum();
	}

	public final LHCoordinateSystem getCoordinateSystem() {
		return coordSys;
	}

	public final float getLeft() {
		return minX;
	}

	public final void setRenderer(Renderer renderer) {
		this.renderer = renderer;
		this.renderer.setSceneGraph(sceneGraph);
		this.renderer.setBackgroundColor(backgroundColor);
		this.renderer.setCamera(this);
	}

	public final void setLens(float lens) {
		this.lens = lens;
	}

	public final float getLens() {
		return lens;
	}

	public final float getRight() {
		return minX + dX;
	}

	public final float getTop() {
		return maxY;
	}

	public final float getBottom() {
		return maxY - dY;
	}

	public final float getZNear() {
		return zNear;
	}

	public final float getZFar() {
		return zFar;
	}

	/**
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public final boolean isInViewport(int x, int y) {
		return (x >= 0 && x < width && y >= 0 && y < height);
	}

	/**
	 * 
	 */
	public final void stopRendering() {
		isRenderingStopped = true;
	}

	/**
	 * 
	 * @param bkc
	 */
	public final void setBackgroundColor(Spectrum bkc) {
		backgroundColor.set(bkc);
	}

	/**
	 * 
	 * @param dest
	 * @return
	 */
	public final Spectrum getBackgoundColor(Spectrum dest) {
		dest.set(backgroundColor);
		return dest;
	}

	/**
	 * 
	 * @param eye
	 * @param focus
	 * @param up
	 */
	public final void lookAt(Vector eye, Vector focus, Vector up) {
		Vector d = new Vector(focus);
		d.sub(eye);
		d.normalize();
		d.negate();
		up.normalize();
		Vector newX = new Vector();
		Vector.cross(up, d, newX);
		coordSys.setParentToLocalMatrix(newX.x, up.x, d.x, newX.y, up.y, d.y,
				newX.z, up.z, d.z);
		coordSys.setOrigin(eye);
	}

	/**
	 * 
	 */
	public final void translate(Vector displacement) {
		coordSys.translateInLocal(displacement);
	}

	/**
	 * 
	 * @param p
	 * @param dest
	 * @return
	 */
	public final Vector transPointToLocal(Vector p, Vector dest) {
		return coordSys.transPointToLocal(p, dest);
	}

	/**
	 * 
	 * @param v
	 * @param dest
	 * @return
	 */
	public final Vector transVectorToLocal(Vector v, Vector dest) {
		return coordSys.transVectorToLocal(v, dest);
	}

	/**
	 * 
	 * @param s
	 */
	public final void scaleViewport(float s) {
		dX *= s;
		dY *= s;
		minX *= s;
		maxY *= s;
	}

	/**
	 * 
	 */
	public final void rotate(Vector axis, float angle) {
		coordSys.rotate(axis, angle);
	}

	/**
	 * 
	 * @param width
	 * @param height
	 */
	public final void setViewportSize(int width, int height) {
		this.width = width;
		this.height = height;
	}

	/**
	 * 
	 * @param n
	 */
	public final void setSamplesPerPixel(int n) {
		nSamples = (n < 1 ? 1 : n);
	}

	/**
	 * 
	 * @param s
	 */
	public final void setScene(SceneGraph s) {
		sceneGraph = s;
	}

	/**
	 * 
	 * @param minX
	 * @param minY
	 * @param maxX
	 * @param maxY
	 */
	public final void setViewportMap(float minX, float minY, float maxX,
			float maxY) {
		dX = maxX - minX;
		dY = maxY - minY;
		this.minX = minX;
		this.maxY = maxY;
	}

	/**
	 * 
	 * @param zNear
	 */
	public final void setZNear(float zNear) {
		this.zNear = -Math.abs(zNear);
	}

	/**
	 * 
	 * @param zFar
	 */
	public final void setZFar(float zFar) {
		this.zFar = -Math.abs(zFar);
	}

	/**
	 * 
	 * @param x
	 * @param y
	 * @param dest
	 * @return
	 */
	public final Vector getLocalPointFromViewport(int x, int y, Vector dest) {
		dest.set(((float) x / width * dX + minX), (maxY - (float) y / height
				* dY), zNear);
		return dest;
	}

	public final Vector getWorldPointFromViewport(int x, int y, Vector dest) {
		dest.set(((float) x / width * dX + minX), (maxY - (float) y / height
				* dY), zNear);
		coordSys.transPointToParent(dest, dest);
		return dest;
	}

	/**
	 * 
	 * @param p
	 * @return
	 */
	public final int getXFromWorld(Vector p) {
		float px = p.x, py = p.y, pz = p.z;
		coordSys.transPointToLocal(p, p);
		float x = p.x * zNear / (zNear + p.z);
		p.set(px, py, pz);
		return getXFromViewport(x);
	}

	/**
	 * 
	 * @param p
	 * @return
	 */
	public final int getYFromWorld(Vector p) {
		float px = p.x, py = p.y, pz = p.z;
		coordSys.transPointToLocal(p, p);
		float y = p.y * zNear / (zNear + p.z);
		p.set(px, py, pz);
		return getYFromViewport(y);
	}

	/**
	 * 
	 * @param p
	 * @return
	 */
	public final int getXFromLocal(Vector p) {
		float x = p.x * zNear / (zNear + p.z);
		return getXFromViewport(x);
	}

	/**
	 * 
	 * @param p
	 * @return
	 */
	public final int getYFromLocal(Vector p) {
		float y = p.y * zNear / (zNear + p.z);
		return getYFromViewport(y);
	}

	/**
	 * 
	 * @param x
	 * @return
	 */
	public final int getXFromViewport(float x) {
		return (int) ((x - minX) * width / dX);
	}

	/**
	 * 
	 * @param y
	 * @return
	 */
	public final int getYFromViewport(float y) {
		return (int) ((maxY - y) * height / dY);
	}

	/**
	 * 
	 * @return
	 */
	public final int getWidth() {
		return width;
	}

	/**
	 * 
	 * @return
	 */
	public final int getHeight() {
		return height;
	}

	/**
	 * 
	 * @return
	 */
	public final SceneGraph getSceneGraph() {
		return sceneGraph;
	}

	/**
	 * 
	 * @return
	 */
	public final int getSampleNumPerPixel() {
		return nSamples;
	}

	/**
	 * 
	 * @param img
	 * @param panel
	 */
	public final void render(BufferedImage img, Component panel) {
		sceneGraph.finish();

		renderer.prepareForRendering();
		isRenderingStopped = false;

		int nCores = Runtime.getRuntime().availableProcessors();
		RenderingThread[] threads = new RenderingThread[nCores];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new RenderingThread(nCores, i, img, panel);
		}
		for (int i = 0; i < threads.length; i++) {
			threads[i].start();
		}
		try {
			for (int i = 0; i < threads.length; i++) {
				threads[i].join();
			}
		} catch (InterruptedException ie) {
			ie.printStackTrace();
		}
	}

	/**
	 * 
	 * @param ray
	 */
	private final void launchRenderer(Ray ray) {
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
	public final Ray getRayFromViewport(int x, int y, float xOffset,
			float yOffset, Ray dest) {
		Vector o = new Vector(), d = new Vector();
		getLocalPointFromViewport(x, y, d);
		d.x += xOffset;
		d.y += yOffset;
		d.normalize();
		coordSys.transPointToParent(origin, o);
		coordSys.transVectorToParent(d, d);
		dest.setOrigin(o);
		dest.setDirection(d);
		return dest;
	}

	/**
	 * 
	 * @param gl
	 * @param glu
	 */
	public final void drawScene(GL2 gl, GLU glu) {
		for (int i = 0; i < sceneGraph.getMeshNum(); i++) {
			sceneGraph.getMesh(i).draw(gl, glu, this);
		}
	}

	private final class RenderingThread extends Thread {
		private int nCores;
		private int iCore;
		private BufferedImage img;
		private Component panel;

		public RenderingThread(int nCores, int iCore, BufferedImage img,
				Component panel) {
			this.nCores = nCores;
			this.iCore = iCore;
			this.img = img;
			this.panel = panel;
		}

		public final void run() {
			Ray ray = new Ray();
			Spectrum rayColor = ray.getSpectrum();
			Vector d = new Vector();
			ray.getDirection(d);
			Vector c = new Vector();
			ray.getOrigin(c);
			Vector buf = new Vector();
			float sampleGap = getLocalPointFromViewport(0, 0, buf).x;
			sampleGap = (sampleGap - getLocalPointFromViewport(1, 0, buf).x);

			// float[] xOffset = new float[sampleNum], yOffset = new
			// float[sampleNum];
			// for (int i = 0; i < nSamples; i++)
			// {
			// xOffset[i] = (float)Math.random() * sampleGap;
			// yOffset[i] = (float)Math.random() * sampleGap;
			// }
			float[][] xJittered = new float[nSamples][nSamples], yJittered = new float[nSamples][nSamples];
			for (int i = 0; i < nSamples; i++) {
				for (int j = 0; j < nSamples; j++) {
					xJittered[i][j] = (i + (float) Math.random()) / nSamples
							* sampleGap;
					yJittered[i][j] = (j + (float) Math.random()) / nSamples
							* sampleGap;
				}
			}

			float[][] xOffset = new float[nSamples][nSamples], yOffset = new float[nSamples][nSamples];
			for (int i = 0; i < nSamples; i++) {
				for (int j = 0; j < nSamples; j++) {
					float r = lens * (float) Math.sqrt(Math.random());
					double phy = 2 * Math.PI * Math.random();
					xOffset[i][j] = r * (float) Math.cos(phy);
					yOffset[i][j] = r * (float) Math.sin(phy);
				}
			}

			/*
			 * 
			 */
			float scale = 1f / (nSamples * nSamples);
			int x = iCore, y = 0;
			while (y < height) {
				if (isRenderingStopped) {
					return;
				}
				rayColor.set(0f, 0f, 0f);
				for (int i = 0; i < nSamples; i++) {
					for (int j = 0; j < nSamples; j++) {
						c.set(origin);
						c.add(xOffset[i][j], yOffset[i][j], 0);
						getLocalPointFromViewport(x, y, d);
						d.x += xJittered[i][j];
						d.y += yJittered[i][j];
						Vector.sub(d, c, d);
						d.normalize();
						coordSys.transVectorToParent(d, d);
						coordSys.transPointToParent(c, c);
						ray.setDirection(d);
						ray.setOrigin(c);
						ray.setLength(Float.MAX_VALUE);
						launchRenderer(ray);
					}
				}
				rayColor.multiply(scale);

				int argb = rayColor.toARGB();
				img.setRGB(x, y, argb);

				x += nCores;
				if (x >= width) {
					x -= width;
					++y;
					panel.repaint();
				}
			}
		}
	}

	public final TriangleMesh pick(int x, int y) {
		Ray ray = new Ray();
		getRayFromViewport(x, y, 0, 0, ray);
		RayIntersectionInfo ir = new RayIntersectionInfo();
		sceneGraph.intersect(ray, ir);
		if (ir.isIntersected()) {
			return ir.getTriangle().getTriangleMesh();
		}
		return null;
	}

	@Override
	public void draw(GL2 gl, GLU glu, Camera camera) {
	}
}
