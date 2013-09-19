package prime.core;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.Observable;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

import prime.math.LHCoordinateSystem;
import prime.math.Transformable;
import prime.math.Vec3;
import prime.model.RayIntersectionInfo;
import prime.model.TriangleMesh;
import prime.physics.Ray;
import prime.physics.Spectrum;

/**
 * 
 * @author lizhaoliu
 *
 */
public class Camera extends Observable implements Serializable,
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
	private Vec3 origin = new Vec3(0f, 0f, 0f);

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

	public LHCoordinateSystem getCoordinateSystem() {
		return coordSys;
	}

	public float getLeft() {
		return minX;
	}

	public void setRenderer(Renderer renderer) {
		this.renderer = renderer;
		this.renderer.setSceneGraph(sceneGraph);
		this.renderer.setBackgroundColor(backgroundColor);
		this.renderer.setCamera(this);
	}

	public void setLens(float lens) {
		this.lens = lens;
	}

	public float getLens() {
		return lens;
	}

	public float getRight() {
		return minX + dX;
	}

	public float getTop() {
		return maxY;
	}

	public float getBottom() {
		return maxY - dY;
	}

	public float getZNear() {
		return zNear;
	}

	public float getZFar() {
		return zFar;
	}

	/**
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public boolean isInViewport(int x, int y) {
		return (x >= 0 && x < width && y >= 0 && y < height);
	}

	/**
	 * 
	 */
	public void stopRendering() {
		isRenderingStopped = true;
	}

	/**
	 * 
	 * @param bkc
	 */
	public void setBackgroundColor(Spectrum bkc) {
		backgroundColor.set(bkc);
	}

	/**
	 * 
	 * @param dest
	 * @return
	 */
	public Spectrum getBackgoundColor(Spectrum dest) {
		dest.set(backgroundColor);
		return dest;
	}

	/**
	 * 
	 * @param eye
	 * @param focus
	 * @param up
	 */
	public void lookAt(Vec3 eye, Vec3 focus, Vec3 up) {
		Vec3 d = new Vec3(focus);
		d.sub(eye);
		d.normalize();
		d.negate();
		up.normalize();
		Vec3 newX = new Vec3();
		Vec3.cross(up, d, newX);
		coordSys.setParentToLocalMatrix(newX.x, up.x, d.x, newX.y, up.y, d.y,
				newX.z, up.z, d.z);
		coordSys.setOrigin(eye);
	}

	/**
	 * 
	 */
	public void translate(Vec3 displacement) {
		coordSys.translateInLocal(displacement);
	}

	/**
	 * 
	 * @param p
	 * @param dest
	 * @return
	 */
	public Vec3 transPointToLocal(Vec3 p, Vec3 dest) {
		return coordSys.transPointToLocal(p, dest);
	}

	/**
	 * 
	 * @param v
	 * @param dest
	 * @return
	 */
	public Vec3 transVectorToLocal(Vec3 v, Vec3 dest) {
		return coordSys.transVectorToLocal(v, dest);
	}

	/**
	 * 
	 * @param s
	 */
	public void scaleViewport(float s) {
		dX *= s;
		dY *= s;
		minX *= s;
		maxY *= s;
	}

	/**
	 * 
	 */
	public void rotate(Vec3 axis, float angle) {
		coordSys.rotate(axis, angle);
	}

	/**
	 * 
	 * @param width
	 * @param height
	 */
	public void setViewportSize(int width, int height) {
		this.width = width;
		this.height = height;
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
	public void setScene(SceneGraph s) {
		sceneGraph = s;
	}

	/**
	 * 
	 * @param minX
	 * @param minY
	 * @param maxX
	 * @param maxY
	 */
	public void setViewportMap(float minX, float minY, float maxX,
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
	 * @param dest
	 * @return
	 */
	public Vec3 getLocalPointFromViewport(int x, int y, Vec3 dest) {
		dest.set(((float) x / width * dX + minX), (maxY - (float) y / height
				* dY), zNear);
		return dest;
	}

	public Vec3 getWorldPointFromViewport(int x, int y, Vec3 dest) {
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
	public int getXFromWorld(Vec3 p) {
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
	public int getYFromWorld(Vec3 p) {
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
	public int getXFromLocal(Vec3 p) {
		float x = p.x * zNear / (zNear + p.z);
		return getXFromViewport(x);
	}

	/**
	 * 
	 * @param p
	 * @return
	 */
	public int getYFromLocal(Vec3 p) {
		float y = p.y * zNear / (zNear + p.z);
		return getYFromViewport(y);
	}

	/**
	 * 
	 * @param x
	 * @return
	 */
	public int getXFromViewport(float x) {
		return (int) ((x - minX) * width / dX);
	}

	/**
	 * 
	 * @param y
	 * @return
	 */
	public int getYFromViewport(float y) {
		return (int) ((maxY - y) * height / dY);
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
	public SceneGraph getSceneGraph() {
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
	 * 
	 * @param img
	 * @param panel
	 */
	public void render(BufferedImage img, Component panel) {
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
	private void launchRenderer(Ray ray) {
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
	public Ray getRayFromViewport(int x, int y, float xOffset,
			float yOffset, Ray dest) {
		Vec3 o = new Vec3(), d = new Vec3();
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
	public void drawScene(GL2 gl, GLU glu) {
		for (int i = 0; i < sceneGraph.getMeshNum(); i++) {
			sceneGraph.getMesh(i).draw(gl, glu, this);
		}
	}

	private class RenderingThread extends Thread {
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

		public void run() {
			Ray ray = new Ray();
			Spectrum rayColor = ray.getSpectrum();
			Vec3 d = new Vec3();
			ray.getDirection(d);
			Vec3 c = new Vec3();
			ray.getOrigin(c);
			Vec3 buf = new Vec3();
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
						Vec3.sub(d, c, d);
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

	public TriangleMesh pick(int x, int y) {
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
