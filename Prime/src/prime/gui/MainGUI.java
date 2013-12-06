package prime.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLJPanel;
import javax.media.opengl.glu.GLU;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;


import prime.core.Camera;
import prime.core.PathTracer;
import prime.core.Renderer;
import prime.core.SceneGraph;
import prime.math.ConeFilter;
import prime.math.Filter;
import prime.math.GaussianFilter;
import prime.math.LHCoordinateSystem;
import prime.math.Vec3;
import prime.model.TriangleMesh;
import prime.physics.BSDF;
import prime.physics.IdealDiffuseModel;
import prime.physics.Spectrum;
import prime.util.ModelFileLoader;

/**
 * a hand-written gui
 * @author lizhaoliu
 *
 */
public class MainGUI extends JFrame {
    private static final long serialVersionUID = -739564126009719851L;

    public static final int PANEL_HEIGHT = (int) (Toolkit.getDefaultToolkit()
	    .getScreenSize().height / 1.2);
    public static final int PANEL_WIDTH = (int) (Toolkit.getDefaultToolkit()
	    .getScreenSize().height / 1.2);

    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;

    private ViewPanel viewPanel;

    private Camera camera;
    private SceneGraph sceneGraph;

    private TriangleMesh selectedMesh;

    private RenderDialog renderDialog;
    private BSDFEditorDialog bsdfEditDialog;
    // private TexEditorDialog texEditDialog;
    private WestPanel westPanel;

    private List<BSDF> bsdfList = new ArrayList<BSDF>(10);
    private List<TriangleMesh> meshesList = new ArrayList<TriangleMesh>();

    private ModelFileLoader loader = new ModelFileLoader(MainGUI.this);

    public static void main(String[] args) {
	new MainGUI();
    }

    public MainGUI() {
	setTitle("Prime");
	setVisible(true);
	setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
	setResizable(false);
	init();
	initDialogs();
	addWindowListener(new WindowListener() {
	    public void windowOpened(WindowEvent e) {
	    }

	    public void windowIconified(WindowEvent e) {
	    }

	    public void windowDeiconified(WindowEvent e) {
	    }

	    public void windowDeactivated(WindowEvent e) {
	    }

	    public void windowClosing(WindowEvent e) {
		int res = JOptionPane.showConfirmDialog(MainGUI.this,
			"Are you sure to quit?", "Confirming",
			JOptionPane.YES_NO_OPTION);
		if (res == JOptionPane.YES_OPTION) {
		    System.exit(0);
		}
	    }

	    public void windowClosed(WindowEvent e) {
	    }

	    public void windowActivated(WindowEvent e) {
	    }
	});
	Box centralBox = Box.createHorizontalBox();
	centralBox.add(viewPanel = new ViewPanel(camera));
	add(new ButtonPanel(), BorderLayout.NORTH);
	add(centralBox, BorderLayout.CENTER);
	add(westPanel = new WestPanel(), BorderLayout.WEST);
	// add(new ControlPanel(), BorderLayout.WEST);
	pack();
    }

    private void init() {
	sceneGraph = new SceneGraph();
	camera = new Camera(PANEL_WIDTH, PANEL_HEIGHT);
	camera.setZNear(-1);
	camera.setZFar(-1000);
	camera.setViewportMap(-.5f, -.5f, .5f, .5f);
	camera.setScene(sceneGraph);
	sceneGraph.setMaxBSPDivisionDepth(15);

	BSDF defaultMat = new IdealDiffuseModel();
	// BSDF defaultMat = new OrenNayerModel();
	defaultMat.setName("default");
	bsdfList.add(defaultMat);
    }

    private void initDialogs() {
	renderDialog = new RenderDialog();
	bsdfEditDialog = new BSDFEditorDialog(750, 240);
	// texEditDialog = new TexEditorDialog(640, 480);
    }

    private class WestPanel extends JPanel {
	private static final long serialVersionUID = -1751120729267132287L;

	private JTree tree = new JTree();
	private JTextArea txtArea = new JTextArea();

	public WestPanel() {
	    this.setPreferredSize(new Dimension((int) (PANEL_WIDTH / 2.5),
		    PANEL_HEIGHT));
	    Dimension dim = new Dimension(this.getPreferredSize().width, this
		    .getPreferredSize().height / 2);
	    tree.setRootVisible(false);
	    Box vertBox = Box.createVerticalBox();
	    JScrollPane spModlExplr = new JScrollPane(tree);
	    spModlExplr.setPreferredSize(dim);
	    spModlExplr.setBorder(new TitledBorder("Models Explorer"));
	    txtArea.setPreferredSize(dim);
	    txtArea.setEditable(false);
	    JScrollPane spTxtArea = new JScrollPane(txtArea);
	    spTxtArea.setPreferredSize(dim);
	    spTxtArea.setBorder(new TitledBorder("System Log"));
	    vertBox.add(spModlExplr);
	    vertBox.add(spTxtArea);
	    this.add(vertBox);
	    updateModelsData();
	}

	public final void updateModelsData() {
	    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
	    for (TriangleMesh mesh : meshesList) {
		DefaultMutableTreeNode node = new DefaultMutableTreeNode(mesh);
		DefaultMutableTreeNode trianglesNode = new DefaultMutableTreeNode(
			mesh.getTrianglesNum());
		node.add(trianglesNode);
		root.add(node);
	    }
	    tree.removeAll();
	    DefaultTreeModel model = new DefaultTreeModel(root);
	    tree.setModel(model);
	}

	public final void addSystemLog(String info) {
	    txtArea.append(info + "\n");
	}
    }

    private class RenderDialog extends JDialog {
	private static final long serialVersionUID = -8582492154611863437L;

	public static final int RENDERDLG_WIDTH = 660;
	public static final int RENDERDLG_HEIGHT = 660;

	private ResultPanel resPanel;
	private BufferedImage resImage;
	private Renderer[] renderers = { new PathTracer() };
	private Renderer selectedRenderer = renderers[0];
	private Filter[] filters = { new ConeFilter(1), new GaussianFilter() };
	private Filter selectedFilter = filters[0];

	public RenderDialog() {
	    setTitle("Render Settings");
	    add(resPanel = new ResultPanel(), BorderLayout.CENTER);
	    add(new ContPanel(), BorderLayout.EAST);
	    setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
	    setVisible(false);
	    setResizable(false);
	    pack();
	}

	private class ResultPanel extends JPanel implements MouseListener,
		MouseMotionListener, MouseWheelListener {
	    private static final long serialVersionUID = 2436178488034586868L;

	    private int startX, startY;
	    private float paintWidth, paintHeight;

	    public ResultPanel() {
		resImage = new BufferedImage(500, 500,
			BufferedImage.TYPE_INT_RGB);
		setPreferredSize(new Dimension(RENDERDLG_WIDTH,
			RENDERDLG_HEIGHT));

		startX = (RENDERDLG_WIDTH - resImage.getWidth()) / 2;
		startY = (RENDERDLG_HEIGHT - resImage.getHeight()) / 2;

		paintWidth = resImage.getWidth();
		paintHeight = resImage.getHeight();

		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
	    }

	    @Override
	    protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.setColor(Color.GRAY);
		g.fillRect(0, 0, RENDERDLG_WIDTH, RENDERDLG_HEIGHT);
		// int w = resImage.getWidth(), h = resImage.getHeight();
		// if (w > getWidth() || h > getHeight())
		// {
		// resPanel.setPreferredSize(new Dimension(w, h));
		// contPanel.setPreferredSize(new
		// Dimension(contPanel.getWidth(), h));
		// RenderDialog.this.pack();
		// g.drawImage(resImage, 0, 0, null);
		// }
		g.drawImage(resImage, startX, startY, (int) paintWidth,
			(int) paintHeight, null);
		g.setColor(Color.RED);
		g.drawString("output", startX, startY + 10);
	    }

	    private boolean isLButtonDown;
	    private int pressX, pressY;

	    @Override
	    public void mouseClicked(MouseEvent arg0) {
		// TODO Auto-generated method stub
		if (arg0.getButton() == MouseEvent.BUTTON3) {
		    startX = (RENDERDLG_WIDTH - resImage.getWidth()) / 2;
		    startY = (RENDERDLG_HEIGHT - resImage.getHeight()) / 2;
		    paintWidth = resImage.getWidth();
		    paintHeight = resImage.getHeight();
		    repaint();
		}
	    }

	    @Override
	    public void mouseEntered(MouseEvent arg0) {
		// TODO Auto-generated method stub
		setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
	    }

	    @Override
	    public void mouseExited(MouseEvent arg0) {
		// TODO Auto-generated method stub
	    }

	    @Override
	    public void mousePressed(MouseEvent arg0) {
		// TODO Auto-generated method stub
		if (arg0.getButton() == MouseEvent.BUTTON1) {
		    isLButtonDown = true;
		    pressX = arg0.getX();
		    pressY = arg0.getY();
		}
	    }

	    @Override
	    public void mouseReleased(MouseEvent arg0) {
		// TODO Auto-generated method stub
		isLButtonDown = false;
	    }

	    @Override
	    public void mouseDragged(MouseEvent arg0) {
		// TODO Auto-generated method stub
		if (isLButtonDown) {
		    int x = arg0.getX(), y = arg0.getY();
		    startX += (x - pressX);
		    startY += (y - pressY);
		    pressX = x;
		    pressY = y;
		    repaint();
		}
	    }

	    @Override
	    public void mouseMoved(MouseEvent arg0) {
		// TODO Auto-generated method stub
	    }

	    @Override
	    public void mouseWheelMoved(MouseWheelEvent arg0) {
		// TODO Auto-generated method stub
		int count = arg0.getWheelRotation();
		if (count < 0) {
		    paintWidth *= 1.05;
		    paintHeight *= 1.05;
		} else {
		    paintWidth *= 0.95;
		    paintHeight *= 0.95;
		}
		repaint();
	    }
	}

	private class ContPanel extends JPanel implements ActionListener {
	    private static final long serialVersionUID = -6635981662091939121L;
	    private int width = 480, height = RENDERDLG_HEIGHT;
	    private JTextField tfStatus;
	    private JButton bStartRendering, bSaveImg, bStop;
	    private SliderPanel sSamples, sDepth, sBspDepth, sTrianglePerNode;
	    private JComboBox cW, cH, cRenders;
	    private SpectrumChooserPanel colorChooser;
	    private JTextField tfLens;
	    private JTextField[] skyDirTFs = {new JTextField(5),new JTextField(5), new JTextField(5)}; 
	    

	    private Integer[] ws = { 100, 200, 300, 400, 500, 600, 700, 800 };
	    private Integer[] hs = { 100, 200, 300, 400, 500, 600, 700, 800 };

	    public ContPanel() {
		setPreferredSize(new Dimension(width, height));
		// setLayout(new GridLayout(5, 2));
		setLayout(new FlowLayout());
		setBorder(new TitledBorder("Rendering parameters"));

		bStartRendering = new JButton("Let's roll");
		bStartRendering.setBackground(Color.YELLOW);
		bStop = new JButton("Stop Rendering");
		bStop.setEnabled(false);
		bSaveImg = new JButton("Save Image");
		tfStatus = new JTextField(16);
		tfStatus.setEditable(false);
		cW = new JComboBox(ws);
		cW.setSelectedIndex(4);
		cH = new JComboBox(hs);
		cH.setSelectedIndex(4);
		cRenders = new JComboBox(renderers);
		cRenders.setSelectedIndex(0);
		sSamples = new SliderPanel(1, 64, 1, 1, "Samples per pixel");
		sDepth = new SliderPanel(1, 25, 1, 1, "Ray tracing depth");
		sBspDepth = new SliderPanel(10, 40, 18, 1, "Max BSP depth");
		sTrianglePerNode = new SliderPanel(1, 5, 3, 1, "Max triangles per BSP leaf");
		bStartRendering.addActionListener(this);
		bStop.addActionListener(this);
		bSaveImg.addActionListener(this);
		colorChooser = new SpectrumChooserPanel();
		add(new JLabel("Image width"));
		add(cW);
		add(new JLabel("Image height"));
		add(cH);
		add(sBspDepth);
		add(sTrianglePerNode);
		add(new JLabel("Select renderer"));
		add(cRenders);
		add(sSamples);
		add(sDepth);
		add(new JLabel("Select background color"));
		add(colorChooser);
		add(new JLabel("Camera Lens : "));
		add(tfLens = new JTextField("" + camera.getLens(), 5));
		add(new JLabel("Sky light direction:"));
		for (JTextField tf : skyDirTFs) {
		    tf.setText("-1");
		    add(tf);
		}
		add(bStartRendering);
		add(bStop);
		add(bSaveImg);
		add(tfStatus);
	    }

	    // protected void paintComponent(Graphics g)
	    // {
	    // super.paintComponents(g);
	    // Graphics2D g2d = (Graphics2D)g;
	    // g2d.setPaint(new GradientPaint(0, 0, Color.LIGHT_GRAY, 0,
	    // getHeight(), Color.BLACK));
	    // g2d.fillRect(0, 0, getWidth(), getHeight());
	    // }

	    public void actionPerformed(ActionEvent ae) {
		Object o = ae.getSource();
		int w = ws[cW.getSelectedIndex()], h = hs[cH.getSelectedIndex()];
		int depth = (int) sDepth.getValue(), samples = (int) sSamples.getValue();
		if (o == bStop) {
		    camera.stopRendering();
		} else if (o == bStartRendering) {
		    sceneGraph.getSky().setDirection(new Vec3(Float.parseFloat(skyDirTFs[0].getText()), 
			    Float.parseFloat(skyDirTFs[1].getText()), 
			    Float.parseFloat(skyDirTFs[2].getText())));
		    selectedRenderer = (Renderer) cRenders.getSelectedItem();
		    selectedRenderer.setFilter(selectedFilter);
		    camera.setRenderer(selectedRenderer);
		    camera.setBackgroundColor(colorChooser
			    .getSelectedSpectrum());
		    camera.setLens(Float.parseFloat(tfLens.getText()));
		    sceneGraph.getSky().setSpectrum(colorChooser.getSelectedSpectrum());
		    int bspDepth = (int) sBspDepth.getValue();
		    sceneGraph.setMaxBSPDivisionDepth(bspDepth);
		    int maxTrianlgesPerNode = (int) sTrianglePerNode.getValue();
		    sceneGraph.setMaxTrianglesPerBSPNode(maxTrianlgesPerNode);
		    selectedRenderer.setBouncingDepth(depth);
		    camera.setSamplesPerPixel(samples);
		    resImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		    new RenderingThread(w, h).start();
		} else if (o == bSaveImg) {
		    JFileChooser chooser = new JFileChooser();
		    FileNameExtensionFilter filter = new FileNameExtensionFilter(
			    "imgae file *.jpg *.bmp *.png *.gif", "png", "jpg",
			    "jpeg", "bmp", "gif");
		    chooser.setFileFilter(filter);
		    int ret = chooser.showSaveDialog(renderDialog);
		    if (ret == JFileChooser.APPROVE_OPTION) {
			try {
			    File file = chooser.getSelectedFile();
			    String extName = file.getName();
			    extName = extName.substring(extName
				    .lastIndexOf('.') + 1);
			    ImageIO.write(resImage, extName, file);
			    JOptionPane.showMessageDialog(renderDialog,
				    "saved to  " + file.getAbsolutePath());
			} catch (Exception ioe) {
			    ioe.printStackTrace();
			}
		    }
		}
	    }

	    private final class RenderingThread extends Thread {
		private int w, h;

		public RenderingThread(int w, int h) {
		    this.w = w;
		    this.h = h;
		}

		public void run() {
		    setViewPanelEnabled(false);
		    int wi = camera.getWidth(), hei = camera.getHeight();
		    camera.setViewportSize(w, h);
		    bStartRendering.setEnabled(false);
		    bStop.setEnabled(true);
		    tfStatus.setText("Working...");

		    addSystemLog("--rendering start--");
		    addSystemLog(Runtime.getRuntime().availableProcessors()
			    + " cores are found.");
		    addSystemLog(sceneGraph.getTrianglesNum() + " triangles.");

		    long t = System.nanoTime();
		    camera.render(resImage, resPanel);
		    t = System.nanoTime() - t;
		    int seconds = (int) (t / 1000000000);
		    int hours = seconds / 3600;
		    int mins = (seconds - hours * 3600) / 60;
		    seconds = seconds - hours * 3600 - mins * 60;
		    String time = hours + "h " + mins + "m " + seconds + "s.";
		    tfStatus.setText("Done-- " + time);

		    addSystemLog("time elapsed : " + time);
		    addSystemLog("--rendering finished--");
		    bStartRendering.setEnabled(true);
		    bStop.setEnabled(false);
		    camera.setViewportSize(wi, hei);
		    MainGUI.this.setViewPanelEnabled(true);
		}
	    }
	}
    }

    // private class TexEditorDialog extends JDialog
    // {
    // private static final long serialVersionUID = 5755031097232026158L;
    //		
    // private BSDF mat;
    // private JButton bOk;
    // private BufferedImage tex, img;
    // private EditorPanel panel;
    // private int imgSize = 256;
    // private int imgX, imgY, texX, texY;
    // private int[] xt = new int[4], yt = new int[4];
    // private int selectedPoint = -1;
    //		
    // public TexEditorDialog(int w, int h)
    // {
    // setSize(w, h);
    // setTitle("Texture Editor");
    // img = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_RGB);
    // panel = new EditorPanel(w, h * 7 / 8);
    // setLayout(new FlowLayout());
    // bOk = new JButton("Ok");
    // bOk.addActionListener(new ActionRes());
    // add(panel);
    // add(bOk);
    // }
    //		
    // private class ActionRes implements ActionListener
    // {
    // public void actionPerformed(ActionEvent ae)
    // {
    // Object o = ae.getSource();
    // if (o == bOk)
    // {
    // TexEditorDialog.this.setVisible(false);
    // }
    // }
    // }
    //		
    // public void setMaterial(BSDF mat)
    // {
    // this.mat = mat;
    // }
    //		
    // private class EditorPanel extends JPanel implements MouseListener,
    // MouseMotionListener
    // {
    // private static final long serialVersionUID = -5039739356706730124L;
    // private int gap = 20;
    //			
    // public EditorPanel(int w, int h)
    // {
    // setPreferredSize(new Dimension(w, h));
    // imgX = gap;
    // imgY = (h - imgSize) / 2;
    // texX = w - (imgSize + gap);
    // texY = imgY;
    // addMouseListener(this);
    // addMouseMotionListener(this);
    // }
    //			
    // protected void paintComponent(Graphics g)
    // {
    // super.paintComponent(g);
    // g.setColor(Color.DARK_GRAY);
    // g.fillRect(0, 0, getWidth(), getHeight());
    //				
    // g.setColor(Color.RED);
    // g.drawString("mapping result", imgX, imgY - 5);
    // drawImg();
    // g.drawImage(img, imgX, imgY, imgSize, imgSize, null);
    //				
    // g.setColor(Color.RED);
    // g.drawString("texture image", texX, texY - 5);
    // g.drawImage(tex, texX, texY, imgSize, imgSize, null);
    //				
    // if (mat != null)
    // {
    // int h = imgY + imgSize;
    // Vector2 v = new Vector2();
    // mat.getTexCoord(0, v);
    // g.setColor(Color.BLUE);
    // g.drawString("Texture coordinate 1: " + v, imgX, h += 10);
    // mat.getTexCoord(1, v);
    // g.setColor(Color.GREEN);
    // g.drawString("Texture coordinate 2: " + v, imgX, h += 10);
    // mat.getTexCoord(2, v);
    // g.setColor(Color.MAGENTA);
    // g.drawString("Texture coordinate 3: " + v, imgX, h += 10);
    // mat.getTexCoord(3, v);
    // g.setColor(Color.YELLOW);
    // g.drawString("Texture coordinate 4: " + v, imgX, h += 10);
    // drawLines(g);
    // }
    // }
    //			
    // private void drawLines(Graphics g)
    // {
    // Vector2 t = new Vector2();
    // for (int i = 0; i < 4; i++)
    // {
    // mat.getTexCoord(i, t);
    // drawLine(i, t, g);
    // }
    // g.setColor(Color.WHITE);
    // g.drawLine(xt[0], yt[0], xt[1], yt[1]);
    // g.drawLine(xt[1], yt[1], xt[2], yt[2]);
    // g.drawLine(xt[2], yt[2], xt[3], yt[3]);
    // g.drawLine(xt[3], yt[3], xt[0], yt[0]);
    // }
    //			
    // private void drawLine(int index, Vector2 t , Graphics g)
    // {
    // int x = (int)(texX + imgSize * t.x), y = (int)(texY + imgSize * t.y);
    // xt[index] = x;
    // yt[index] = y;
    // switch (index)
    // {
    // case 0:
    // g.setColor(Color.BLUE);
    // g.drawLine(imgX, imgY, x, y);
    // break;
    // case 1:
    // g.setColor(Color.GREEN);
    // g.drawLine(imgX + imgSize, imgY, x, y);
    // break;
    // case 2:
    // g.setColor(Color.MAGENTA);
    // g.drawLine(imgX + imgSize, imgY + imgSize, x, y);
    // break;
    // default:
    // g.setColor(Color.YELLOW);
    // g.drawLine(imgX, imgY + imgSize, x, y);
    // break;
    // }
    // g.drawArc(x - 6, y - 6, 12, 12, 0, 360);
    // }
    //			
    // private void drawImg()
    // {
    // float x, y;
    // Spectrum c = new Spectrum();
    // if (mat != null && mat.hasTexture())
    // {
    // for (int i = 0; i < imgSize; i++)
    // {
    // for (int j = 0; j < imgSize; j++)
    // {
    // x = (float)i / imgSize;
    // y = (float)j / imgSize;
    // mat.getTexColor(x, y, c);
    // img.setRGB(i, j, c.toARGB());
    // }
    // }
    // }
    // else
    // {
    // Graphics g = img.getGraphics();
    // g.setColor(Color.YELLOW);
    // g.drawString("Texture mapping not enabled", 0, 10);
    // }
    // }
    //
    // private int x, y;
    // public void mouseClicked(MouseEvent e)
    // {
    // }
    // public void mouseEntered(MouseEvent e)
    // {
    // }
    // public void mouseExited(MouseEvent e)
    // {
    // }
    // public void mousePressed(MouseEvent e)
    // {
    // x = e.getX();
    // y = e.getY();
    // selectedPoint = -1;
    // for (int i = 0; i < 4; i++)
    // {
    // if (Math.abs(x - xt[i]) <= 6 && Math.abs(y - yt[i]) <= 6)
    // {
    // selectedPoint = i;
    // }
    // }
    // }
    // public void mouseReleased(MouseEvent e)
    // {
    // }
    // public void mouseDragged(MouseEvent e)
    // {
    // x = e.getX();
    // y = e.getY();
    // if (x > texX && x < texX + imgSize && y > texY && y < texY + imgSize)
    // {
    // if (selectedPoint != -1)
    // {
    // xt[selectedPoint] = x;
    // yt[selectedPoint] = y;
    // mat.setTexCoord(selectedPoint, (float)(x - texX) / imgSize, (float)(y -
    // texY) / imgSize);
    // repaint();
    // }
    // }
    // }
    // public void mouseMoved(MouseEvent e)
    // {
    // }
    // }
    // }

    private class BSDFEditorDialog extends SimpleDialog {
	private static final long serialVersionUID = -1500497763671474467L;

	private BSDF currentBSDF;
	// private BufferedImage texImg;

	private SpectrumChooserPanel emittancePanel, reflectancePanel,
		transmissionPanel, absorptionPanel;
	private JTextField tfName;
	private JButton bSave, bClose, bNewMat, bAssign;
	private SliderPanel spRefrInd;
	private JComboBox cbBSDFList, cbIsLight;
	private String[] isLights = { "Yes", "No" };

	public BSDFEditorDialog(int w, int h) {
	    super(w, h);
	    setTitle("BSDF Editor");
	    panel.setLayout(new GridLayout(5, 6));
	    panel.add(new JLabel("BSDF Name"));
	    panel.add(tfName = new JTextField(8));
	    panel.add(new JLabel("Emittance Spectrum -->"));
	    panel.add(emittancePanel = new SpectrumChooserPanel());
	    panel.add(new JLabel("Reflection Spectrum -->"));
	    panel.add(reflectancePanel = new SpectrumChooserPanel());
	    panel.add(new JLabel("Transmission Spectrum -->"));
	    panel.add(transmissionPanel = new SpectrumChooserPanel());
	    panel.add(new JLabel("Absorption Spectrum -->"));
	    panel.add(absorptionPanel = new SpectrumChooserPanel());
	    panel.add(spRefrInd = new SliderPanel(100, 350, 100, 100,
		    "Refractive Index"));

	    bSave = new JButton("Save");
	    bSave.addActionListener(new ButtonRes());
	    bNewMat = new JButton("New BSDF");
	    bNewMat.addActionListener(new ButtonRes());
	    bClose = new JButton("Close");
	    bClose.addActionListener(new ButtonRes());
	    cbBSDFList = new JComboBox();
	    for (BSDF m : bsdfList) {
		cbBSDFList.addItem(m);
	    }
	    cbBSDFList.addItemListener(new ItemListener() {
		public void itemStateChanged(ItemEvent e) {
		    int i = cbBSDFList.getSelectedIndex();
		    if (i != -1) {
			loadBSDF(bsdfList.get(i));
		    }
		}
	    });
	    panel.add(new JLabel("Is it a light source? -->"));
	    cbIsLight = new JComboBox(isLights);
	    bAssign = new JButton("Assign to selected mesh");
	    bAssign.addActionListener(new ButtonRes());
	    panel.add(cbIsLight);
	    panel.add(bNewMat);
	    panel.add(bSave);
	    panel.add(bAssign);
	    panel.add(bClose);
	    panel.add(cbBSDFList);
	    addComponentListener(new ComponentListener() {
		public void componentHidden(ComponentEvent e) {
		}

		public void componentMoved(ComponentEvent e) {
		}

		public void componentResized(ComponentEvent e) {
		}

		public void componentShown(ComponentEvent e) {
		    if (selectedMesh != null) {
			bAssign.setEnabled(true);
			BSDF m = selectedMesh.getBSDF();
			cbBSDFList.setSelectedItem(m);
		    } else {
			bAssign.setEnabled(false);
			cbBSDFList.setSelectedIndex(0);
		    }
		}
	    });
	    pack();
	}

	private void setBSDFBySliders(BSDF bsdf) {
	    bsdf.setName(tfName.getText());
	    bsdf.setEmittance(emittancePanel.getSelectedSpectrum());
	    bsdf.setReflectance(reflectancePanel.getSelectedSpectrum());
	    bsdf.setTransmission(transmissionPanel.getSelectedSpectrum());
	    bsdf.setAbsorption(absorptionPanel.getSelectedSpectrum());
	    bsdf.setRefractiveIndex(spRefrInd.getValue());
	    bsdf.setIsLight(cbIsLight.getSelectedIndex() == 0 ? true : false);
	}

	private class ButtonRes implements ActionListener {
	    public void actionPerformed(ActionEvent ae) {
		Object o = ae.getSource();
		if (o == bSave) {
		    setBSDFBySliders(currentBSDF);
		} else if (o == bClose) {
		    BSDFEditorDialog.this.setVisible(false);
		} else if (o == bNewMat) {
		    BSDF m = new IdealDiffuseModel();
		    m.setName("BSDF_" + bsdfList.size());
		    bsdfList.add(m);
		    cbBSDFList.addItem(m);
		    cbBSDFList.setSelectedItem(m);
		} else if (o == bAssign) {
		    if (selectedMesh != null) {
			selectedMesh.setBSDF(currentBSDF);
		    }
		}
	    }
	}

	private void loadBSDF(BSDF mat) {
	    this.currentBSDF = mat;
	    displayBSDF();
	}

	private void displayBSDF() {
	    if (currentBSDF == null) {
		return;
	    }
	    tfName.setText("" + currentBSDF.getName());
	    emittancePanel.setSelectedSpectrum(currentBSDF.getEmittance());
	    reflectancePanel.setSelectedSpectrum(currentBSDF.getReflectance());
	    transmissionPanel
		    .setSelectedSpectrum(currentBSDF.getTransmission());
	    absorptionPanel.setSelectedSpectrum(currentBSDF.getAbsorption());
	    spRefrInd.setValue(currentBSDF.getRefractiveIndex());
	    cbIsLight.setSelectedIndex(currentBSDF.isLight() ? 0 : 1);
	}

	// private final class TexDisPanel extends JPanel implements
	// MouseListener
	// {
	// private static final long serialVersionUID = 1092061730680094741L;
	//			
	// public TexDisPanel()
	// {
	// setPreferredSize(new Dimension(100, 100));
	// addMouseListener(this);
	// }
	//			
	// protected void paintComponent(Graphics g)
	// {
	// super.paintComponent(g);
	// if (texImg != null)
	// {
	// g.drawImage(texImg, 0, 0, getWidth() , getHeight(), null);
	// g.setColor(Color.MAGENTA);
	// g.drawString("texture image", 0, 10);
	// }
	// else
	// {
	// g.setColor(Color.MAGENTA);
	// g.drawString("Click for texture image", 0, 10);
	// }
	// }
	//
	// public void mouseClicked(MouseEvent e)
	// {
	// JFileChooser chooser = new JFileChooser();
	// FileNameExtensionFilter filter = new
	// FileNameExtensionFilter("Image file *.JPG *.BMP *.GIF", "jpg",
	// "jpeg", "bmp", "gif");
	// chooser.setFileFilter(filter);
	// int ret = chooser.showOpenDialog(renderDialog);
	// if (ret == JFileChooser.APPROVE_OPTION)
	// {
	// try
	// {
	// File file = chooser.getSelectedFile();
	// texImg = ImageIO.read(file);
	// repaint();
	// }
	// catch (Exception ioe)
	// {
	// ioe.printStackTrace();
	// }
	// }
	// }
	// public void mouseEntered(MouseEvent e)
	// {}
	// public void mouseExited(MouseEvent e)
	// {}
	// public void mousePressed(MouseEvent e)
	// {}
	// public void mouseReleased(MouseEvent e)
	// {}
	// }
    }

    private class SpectrumChooserPanel extends JPanel implements MouseListener {
	private static final long serialVersionUID = 1889527783733521153L;
	private Color color;

	public SpectrumChooserPanel() {
	    setPreferredSize(new Dimension(100, 50));
	    addMouseListener(this);
	}

	protected void paintComponent(Graphics g) {
	    super.paintComponent(g);
	    g.setColor(color);
	    g.fillRect(0, 0, getWidth(), getHeight());
	}

	public void setSelectedSpectrum(Spectrum c) {
	    color = new Color(c.r, c.g, c.b);
	    repaint();
	}

	public Spectrum getSelectedSpectrum() {
	    if (color == null) {
		return new Spectrum(1.0f, 1.0f, 1.0f);
	    }
	    return new Spectrum(color.getRed() / 256.0f,
		    color.getGreen() / 256.0f, color.getBlue() / 256.0f);
	}

	public void mouseClicked(MouseEvent e) {
	    color = JColorChooser.showDialog(bsdfEditDialog, "Choose color",
		    Color.WHITE);
	    repaint();
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}
    }

    private class ButtonPanel extends JPanel {
	private static final long serialVersionUID = 639206566435527693L;

	private int width = PANEL_WIDTH;
	private int height = 54;

	private JButton bRenderDia, bPointLight, bDirectionalLight, bSpotLight,
		bEditMat, bEditTex, bNewScene, bSaveScene, bLoadScene,
		bImptModel;

	public ButtonPanel() {
	    setPreferredSize(new Dimension(width, height));
	    Box hBox = Box.createHorizontalBox();

	    bRenderDia = new JButton(new ImageIcon("renderButton.png"));
	    bRenderDia.addActionListener(new ActionRes());
	    bRenderDia.setToolTipText("Render");
	    bPointLight = new JButton("New Point Light");
	    bPointLight.addActionListener(new ActionRes());
	    bDirectionalLight = new JButton("New Directional Light");
	    bDirectionalLight.addActionListener(new ActionRes());
	    bSpotLight = new JButton("New Spot Light");
	    bSpotLight.addActionListener(new ActionRes());
	    bEditMat = new JButton(new ImageIcon("matEdit.png"));
	    bEditMat.addActionListener(new ActionRes());
	    bEditMat.setToolTipText("Edit BSDF");
	    bEditTex = new JButton("Texture Editor");
	    bEditTex.addActionListener(new ActionRes());
	    bNewScene = new JButton(new ImageIcon("newFile.png"));
	    bNewScene.addActionListener(new ActionRes());
	    bNewScene.setToolTipText("New Scene");
	    bSaveScene = new JButton(new ImageIcon("saveFile.png"));
	    bSaveScene.setToolTipText("Save Scene");
	    bSaveScene.addActionListener(new ActionRes());
	    bLoadScene = new JButton(new ImageIcon("loadFile.gif"));
	    bLoadScene.setToolTipText("Load Scene");
	    bLoadScene.addActionListener(new ActionRes());
	    // bImptModel = new JButton(new ImageIcon("importButton.png"));
	    bImptModel = new JButton(new ImageIcon("importButton.png"));
	    bImptModel.addActionListener(new ActionRes());
	    bImptModel.setToolTipText("Import Model From File");
	    hBox.add(bImptModel);
	    // add(new JLabel("Illuminating"));
	    // add(bPointLight);
	    // add(bDirectionalLight);
	    // add(bSpotLight);
	    // add(new JLabel("Editors"));
	    hBox.add(bEditMat);
	    // add(bEditTex);
	    // add(new JLabel("Rendering"));
	    hBox.add(bRenderDia);
	    // add(new JLabel("Files"));
	    hBox.add(bNewScene);
	    hBox.add(bSaveScene);
	    hBox.add(bLoadScene);

	    add(hBox, BorderLayout.CENTER);
	}

	protected void paintComponent(Graphics g) {
	    super.paintComponents(g);
	    Graphics2D g2d = (Graphics2D) g;
	    g2d.setPaint(new GradientPaint(0, 0, Color.YELLOW, 0, getHeight(),
		    Color.ORANGE));
	    g2d.fillRect(0, 0, getWidth(), getHeight());
	}

	private class ActionRes implements ActionListener {
	    public void actionPerformed(ActionEvent ae) {
		Object o = ae.getSource();
		if (o == bRenderDia) {
		    renderDialog.setVisible(true);
		} else if (o == bEditMat) {
		    bsdfEditDialog.setVisible(true);
		} else if (o == bImptModel) {
		    JFileChooser chooser = new JFileChooser();
		    FileNameExtensionFilter filter = new FileNameExtensionFilter(
			    "model file *.obj *.3ds", "obj", "3ds");
		    chooser.setFileFilter(filter);
		    int ret = chooser.showOpenDialog(null);
		    if (ret == JFileChooser.APPROVE_OPTION) {
			try {
			    File file = chooser.getSelectedFile();
			    TriangleMesh[] meshes = loader.load(file);
			    for (TriangleMesh mesh : meshes) {
				mesh.setBSDF(bsdfList.get(0));
				mesh.finish();
				sceneGraph.addMesh(mesh);
				meshesList.add(mesh);
			    }
			    sceneGraph.setMaxBSPDivisionDepth(10);
			    sceneGraph.finish();
			    viewPanel.display();
			    westPanel.updateModelsData();
			} catch (Exception ioe) {
			    ioe.printStackTrace();
			}
		    }
		} else if (o == bSaveScene) {
		    JFileChooser chooser = new JFileChooser();
		    FileNameExtensionFilter filter = new FileNameExtensionFilter(
			    "Scene file *.sce", "sce");
		    chooser.setFileFilter(filter);
		    int ret = chooser.showSaveDialog(null);
		    if (ret == JFileChooser.APPROVE_OPTION) {
			try {
			    File file = chooser.getSelectedFile();
			    ObjectOutputStream oos = new ObjectOutputStream(
				    new BufferedOutputStream(
					    new FileOutputStream(file)));
			    oos.writeObject(sceneGraph);
			    JOptionPane.showMessageDialog(null, "saved to  "
				    + file.getAbsolutePath(), "Save scence",
				    JOptionPane.OK_OPTION);
			    oos.close();
			} catch (Exception ioe) {
			    ioe.printStackTrace();
			}
		    }
		} else if (o == bLoadScene) {
		    JFileChooser chooser = new JFileChooser();
		    FileNameExtensionFilter filter = new FileNameExtensionFilter(
			    "Scene File *.sce", "sce");
		    chooser.setFileFilter(filter);
		    int ret = chooser.showOpenDialog(null);
		    if (ret == JFileChooser.APPROVE_OPTION) {
			try {
			    File file = chooser.getSelectedFile();
			    ObjectInputStream ois = new ObjectInputStream(
				    new BufferedInputStream(
					    new FileInputStream(file)));
			    sceneGraph = (SceneGraph) ois.readObject();
			    camera.setScene(sceneGraph);
			    viewPanel.display();
			    ois.close();
			} catch (Exception ioe) {
			    ioe.printStackTrace();
			}
		    }
		} else if (o == bNewScene) {
		    sceneGraph.clearScene();
		    viewPanel.display();
		}
	    }
	}
    }

    private class ViewPanel extends GLJPanel implements GLEventListener,
	    MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {
	private static final long serialVersionUID = 3635297711293484185L;
	private Camera cam;

	private GL2 gl;
	private GLU glu;

	private boolean isEnabled = true;

	private static final int AXIS_X = 0;
	private static final int AXIS_Y = 1;
	private static final int AXIS_Z = 2;
	private static final int MOVE_MODE = 3;
	private static final int ROTATE_MODE = 4;
	private static final int PICK_MODE = 5;

	public ViewPanel(Camera cam) {
	    super();
	    addGLEventListener(this);
	    addMouseListener(this);
	    addMouseMotionListener(this);
	    addMouseWheelListener(this);
	    addKeyListener(this);
	    setPreferredSize(new Dimension(MainGUI.PANEL_WIDTH,
		    MainGUI.PANEL_HEIGHT));
	    this.cam = cam;
	    addFocusListener(new FocusListener() {
		public void focusGained(FocusEvent e) {
		    display();
		}

		public void focusLost(FocusEvent e) {
		    display();
		}
	    });
	    addComponentListener(new ComponentListener() {
		public void componentHidden(ComponentEvent e) {
		}

		public void componentMoved(ComponentEvent e) {
		}

		public void componentResized(ComponentEvent e) {
		    Dimension d = getSize();
		    ViewPanel.this.cam.setViewportSize(d.width, d.height);
		}

		public void componentShown(ComponentEvent e) {
		}
	    });
	    relocateCamera();
	}

	public void setEnabled(boolean isEnabled) {
	    this.isEnabled = isEnabled;
	}

	protected void paintComponent(Graphics g) {
	    super.paintComponent(g);
	    g.setColor(Color.RED);
	    g.drawString("perspective", 2, 12);
	}

	public void relocateCamera() {
	    cam.lookAt(new Vec3(30, 0, 30), new Vec3(0, 0, 0),
		    new Vec3(0, 1, 0));
	}

	private void drawGrids(float d, GL2 gl) {
	    int n = 8;
	    float x, z, beg = -d * n, end = d * n;
	    LHCoordinateSystem coSys = cam.getCoordinateSystem();
	    Vec3 v0 = new Vec3(), v1 = new Vec3();
	    gl.glDisable(GL2.GL_LIGHTING);
	    gl.glBegin(GL2.GL_LINES);
	    gl.glColor3f(0.4f, 0.4f, 0.4f);
	    x = beg;
	    n = (n << 1);
	    for (int i = 0; i <= n; i++) {
		v0.set(x, 0, beg);
		v1.set(x, 0, end);
		v0 = coSys.transPointToLocal(v0);
		v1 = coSys.transPointToLocal(v1);
		gl.glVertex3f(v0.x, v0.y, v0.z);
		gl.glVertex3f(v1.x, v1.y, v1.z);
		x += d;
	    }
	    z = beg;
	    for (int i = 0; i <= n; i++) {
		v0.set(beg, 0, z);
		v1.set(end, 0, z);
		v0 = coSys.transPointToLocal(v0);
		v1 = coSys.transPointToLocal(v1);
		gl.glVertex3f(v0.x, v0.y, v0.z);
		gl.glVertex3f(v1.x, v1.y, v1.z);
		z += d;
	    }
	    gl.glEnd();
	    gl.glEnable(GL2.GL_LIGHTING);
	}

	private int lockAxis = AXIS_X;
	private int opMode = PICK_MODE;
	private int button, key;
	private int oldX, oldY;
	@SuppressWarnings("unused")
	private boolean isKeyPressing = false;
	private Vec3 dv = new Vec3(), n = new Vec3();
	private Vec3 v1 = new Vec3(), v2 = new Vec3();
	private Cursor moveCursor = new Cursor(Cursor.MOVE_CURSOR);

	public void mouseWheelMoved(MouseWheelEvent e) {
	    if (!isEnabled) {
		return;
	    }
	    setCursor(new Cursor(Cursor.N_RESIZE_CURSOR));
	    int ticks = e.getWheelRotation();
	    cam.translate(new Vec3(0, 0, -ticks * 3));
	    display();
	}

	public void mouseClicked(MouseEvent e) {
	    if (!isEnabled) {
		return;
	    }
	    switch (button) {
	    case MouseEvent.BUTTON1:
		selectedMesh = cam.pick(e.getX(), e.getY());
		if (selectedMesh != null) {
		    bsdfEditDialog.loadBSDF(selectedMesh.getBSDF());
		}
		viewPanel.display();
		break;

	    case MouseEvent.BUTTON3:
		break;

	    default:
		break;
	    }
	    ;
	}

	public void mouseEntered(MouseEvent e) {
	    if (!isEnabled) {
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
	    } else {
		setCursor(Cursor.getDefaultCursor());
	    }
	}

	public void mouseExited(MouseEvent e) {
	    setCursor(Cursor.getDefaultCursor());
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
	    if (!isEnabled) {
		return;
	    }
	    requestFocus();
	    viewPanel = this;
	    oldX = e.getX();
	    oldY = e.getY();
	    button = e.getButton();
	}

	public void mouseDragged(MouseEvent e) {
	    if (!isEnabled) {
		return;
	    }
	    cam.getLocalPointFromViewport(e.getX(), e.getY(), v2);
	    cam.getLocalPointFromViewport(oldX, oldY, v1);
	    v2.z = cam.getZNear();
	    v1.z = cam.getZNear();
	    dv = Vec3.sub(v1, v2);
	    dv.normalize();
	    switch (button) {
	    case MouseEvent.BUTTON1:
		switch (opMode) {
		case PICK_MODE:
		    viewPanel.display();
		    break;

		case MOVE_MODE:
		    if (selectedMesh != null) {
			switch (lockAxis) {
			case AXIS_X:
			    dv.set(dv.get(0), 0, 0);
			    break;

			case AXIS_Y:
			    dv.set(0, dv.get(0), 0);
			    break;

			case AXIS_Z:
			    dv.set(0, 0, dv.get(0));
			    break;

			default:
			    break;
			}
			;
			viewPanel.display();
		    }
		    oldX = e.getX();
		    oldY = e.getY();
		    break;

		case ROTATE_MODE:
		    v1.normalize();
		    v2.normalize();
		    float angle = (float) (3 * Math.acos(Vec3.dot(v1, v2)) * 180 / Math.PI);
		    if (selectedMesh != null) {
			switch (lockAxis) {
			case AXIS_X:
			    n.set(1, 0, 0);
			    break;

			case AXIS_Y:
			    n.set(0, 1, 0);
			    break;

			case AXIS_Z:
			    n.set(0, 0, 1);
			    break;

			default:
			    break;
			}
			if (v1.x - v2.x < 0) {
			    angle = -angle;
			}
			viewPanel.display();
		    }
		    oldX = e.getX();
		    oldY = e.getY();
		    break;

		default:
		    break;
		}
		break;

	    case MouseEvent.BUTTON2:
		setCursor(moveCursor);
		cam.translate(dv);
		display();
		oldX = e.getX();
		oldY = e.getY();
		break;

	    case MouseEvent.BUTTON3:
		v1.normalize();
		v2.normalize();
		float angle = (float) (3 * Math.acos(Vec3.dot(v1, v2)) * 180 / Math.PI);
		n = Vec3.cross(v1, v2);
		n.normalize();
		cam.rotate(n, angle);
		display();
		oldX = e.getX();
		oldY = e.getY();
		break;

	    default:
		break;
	    }
	    ;
	}

	public void mouseMoved(MouseEvent e) {
	    setCursor(Cursor.getDefaultCursor());
	}

	public void keyPressed(KeyEvent e) {
	    if (!isEnabled) {
		return;
	    }
	    isKeyPressing = true;
	    key = e.getKeyCode();
	    switch (key) {
	    case KeyEvent.VK_R:
		relocateCamera();
		display();
		break;
	    case KeyEvent.VK_DELETE:
		if (selectedMesh != null) {
		    sceneGraph.removeMesh(selectedMesh);
		    selectedMesh = null;
		    sceneGraph.finish();
		    viewPanel.display();
		}
		break;
	    case KeyEvent.VK_X:
		lockAxis = AXIS_X;
		break;
	    case KeyEvent.VK_Y:
		lockAxis = AXIS_Y;
		break;
	    case KeyEvent.VK_Z:
		lockAxis = AXIS_Z;
		break;
	    case KeyEvent.VK_Q:
		opMode = PICK_MODE;
		break;
	    case KeyEvent.VK_W:
		opMode = MOVE_MODE;
		break;
	    case KeyEvent.VK_E:
		opMode = ROTATE_MODE;
		break;
	    default:
		break;
	    }
	}

	public void keyReleased(KeyEvent e) {
	    if (!isEnabled) {
		return;
	    }
	    isKeyPressing = false;
	    key = 0;
	}

	public void keyTyped(KeyEvent e) {
	}

	public void display(GLAutoDrawable drawable) {
	    gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
	    gl.glMatrixMode(GL2.GL_MODELVIEW);
	    gl.glLoadIdentity();
	    cam.drawScene(gl, glu);
	    if (selectedMesh != null) {
			selectedMesh.draw(gl, glu, cam);
			selectedMesh.drawBoundingBox(gl, glu, cam);
	    }
	    drawGrids(5f, gl);
	}

	/**
	 * 
	 * @param drawable
	 * @param arg1
	 * @param arg2
	 */
	@SuppressWarnings("unused")
	public void displayChanged(GLAutoDrawable drawable, boolean arg1,
		boolean arg2) {
	}

	public void init(GLAutoDrawable drawable) {
	    gl = drawable.getGL().getGL2();
	    glu = new GLU();
	    gl.glShadeModel(GL2.GL_SMOOTH);
	    gl.glEnable(GL2.GL_DEPTH_TEST);
	    gl.glCullFace(GL2.GL_NONE);
	    gl.glClearColor(0.54f, 0.68f, 0.78f, 1.0f);

	    gl.glLightModeli(GL2.GL_LIGHT0, GL2.GL_LIGHT_MODEL_TWO_SIDE);
	    gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, new float[] { 1f, 1f, 1f,
		    1f }, 0);
	    gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, new float[] { 0f, 0f,
		    0f, 1f }, 0);
	    gl.glEnable(GL2.GL_LIGHT0);

	    gl.glEnable(GL2.GL_LIGHTING);
	}

	public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
	    cam.setViewportSize(w, h);
	    gl.glViewport(0, 0, w, h);
	    gl.glMatrixMode(GL2.GL_PROJECTION);
	    gl.glLoadIdentity();
	    float r = (float) w / (float) h;
	    float left, right, top, bottom, zNear, zFar;
	    if (r > 1f) {
		left = cam.getLeft() * r;
		right = cam.getRight() * r;
		bottom = cam.getBottom();
		top = cam.getTop();
		zNear = -cam.getZNear();
		zFar = -cam.getZFar();
	    } else {
		left = cam.getLeft();
		right = cam.getRight();
		bottom = cam.getBottom() / r;
		top = cam.getTop() / r;
		zNear = -cam.getZNear();
		zFar = -cam.getZFar();
	    }
	    gl.glFrustum(left, right, bottom, top, zNear, zFar);
	}

	@Override
	public void dispose(GLAutoDrawable arg0) {
		// TODO Auto-generated method stub
		
	}
    }

    private class SliderPanel extends JPanel {
	private static final long serialVersionUID = 5246386435723037503L;
	private JLabel label;
	private String str;
	JSlider s;
	private float divisor;

	public SliderPanel(int min, int max, int init, float divisor,
		String labl) {
	    str = labl;
	    s = new JSlider(min, max, init);
	    s.setMajorTickSpacing(1);
	    s.setSnapToTicks(true);
	    this.divisor = divisor;
	    label = new JLabel(str + ": " + getValue());

	    setLayout(new FlowLayout());
	    add(label);
	    add(s);
	    s.addChangeListener(new ChangeListener() {
		public void stateChanged(ChangeEvent e) {
		    label.setText(str + ": " + getValue());
		}
	    });
	}

	public float getValue() {
	    return (float) s.getValue() / (float) divisor;
	}

	public void setValue(float v) {
	    int n = (int) (v * divisor);
	    s.setValue(n);
	}
    }

    private class SimpleDialog extends JDialog {
	private static final long serialVersionUID = 2292483261566131542L;

	protected JPanel panel;

	public SimpleDialog(int width, int height) {
	    panel = new JPanel();
	    panel.setPreferredSize(new Dimension(width, height));
	    add(panel, BorderLayout.CENTER);
	    setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
	    setVisible(false);
	    setResizable(false);
	    pack();
	}
    }

    public final void addSystemLog(String log) {
	westPanel.addSystemLog(log);
    }

    public final void setViewPanelEnabled(boolean isEnabled) {
	viewPanel.setEnabled(isEnabled);
    }
}