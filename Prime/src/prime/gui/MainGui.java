package prime.gui;

import org.apache.log4j.BasicConfigurator;
import prime.core.Camera;
import prime.core.PathTracer;
import prime.core.Renderer;
import prime.core.Scene;
import prime.math.ConeFilter;
import prime.math.Filter;
import prime.math.GaussianFilter;
import prime.math.LHCoordinateSystem;
import prime.math.Vec3f;
import prime.model.TriangleMesh;
import prime.physics.Color3f;
import prime.physics.IdealDiffuseModel;
import prime.physics.Material;
import prime.util.ContentLoader;

import javax.imageio.ImageIO;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLJPanel;
import javax.media.opengl.glu.GLU;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.*;
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
import java.util.concurrent.Executors;

import static prime.math.MathUtils.cross;
import static prime.math.MathUtils.dot;
import static prime.math.MathUtils.sub;

/**
 * a hand-written gui
 *
 * @author lizhaoliu
 */
public class MainGui extends JFrame {
  private static final long serialVersionUID = -739564126009719851L;

  public static final int PANEL_WIDTH;
  public static final int PANEL_HEIGHT;

  static {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice device = ge.getDefaultScreenDevice();
    PANEL_WIDTH = (int) (device.getDisplayMode().getWidth() / 1.2);
    PANEL_HEIGHT = (int) (device.getDisplayMode().getHeight() / 1.2);
  }

  private ViewPanel viewPanel;

  private Camera camera;
  private Scene sceneGraph;

  private TriangleMesh selectedMesh;

  private RenderDialog renderDialog;
  private MaterialEditorDialog materialEditDialog;
  // private TexEditorDialog texEditDialog;
  private WestPanel westPanel;

  private List<Material> materialList = new ArrayList<>(10);
  private List<TriangleMesh> meshesList = new ArrayList<>();

  private ContentLoader loader = new ContentLoader(MainGui.this);

  public static void main(String[] args) {
    BasicConfigurator.configure();
    new MainGui();
  }

  public MainGui() {
    setTitle("Prime");
    setVisible(true);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setResizable(true);
    init();
    initDialogs();
    Box centralBox = Box.createHorizontalBox();
    centralBox.add(viewPanel = new ViewPanel(camera));
    add(new ButtonPanel(), BorderLayout.NORTH);
    add(centralBox, BorderLayout.CENTER);
    add(westPanel = new WestPanel(), BorderLayout.WEST);
    // add(new ControlPanel(), BorderLayout.WEST);
    pack();
  }

  private void init() {
    sceneGraph = new Scene();
    camera = new Camera(PANEL_WIDTH, PANEL_HEIGHT);
    camera.setZNear(-1);
    camera.setZFar(-1000);
    camera.setViewportMap(-.5f, -.5f, .5f, .5f);
    camera.setScene(sceneGraph);
    sceneGraph.setMaxKdTreeDepth(15);

    Material defaultMat = new IdealDiffuseModel();
    defaultMat.setName("default");
    materialList.add(defaultMat);
  }

  private void initDialogs() {
    renderDialog = new RenderDialog();
    materialEditDialog = new MaterialEditorDialog(750, 240);
    // texEditDialog = new TexEditorDialog(640, 480);
  }

  private class WestPanel extends JPanel {
    private static final long serialVersionUID = -1751120729267132287L;

    private JTree tree = new JTree();
    private JTextArea txtArea = new JTextArea();

    public WestPanel() {
      this.setPreferredSize(new Dimension((int) (PANEL_WIDTH / 3), PANEL_HEIGHT));
      Dimension dim = new Dimension(this.getPreferredSize().width, this.getPreferredSize().height / 2);
      tree.setRootVisible(false);
      tree.addTreeSelectionListener(new TreeSelectionListener() {
        @Override
        public void valueChanged(TreeSelectionEvent arg0) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
          Object obj = node.getUserObject();
          if (obj instanceof TriangleMesh) {
            selectedMesh = (TriangleMesh) obj;
            onMeshSelected();
          }
        }
      });
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
      updateModelTree();
    }

    public final void updateModelTree() {
      DefaultMutableTreeNode root = new DefaultMutableTreeNode();
      for (TriangleMesh mesh : meshesList) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(mesh);
        node.add(new DefaultMutableTreeNode(mesh.getPositionCount() + " vertices"));
        node.add(new DefaultMutableTreeNode(mesh.getNormalCount() + " normals"));
        node.add(new DefaultMutableTreeNode(mesh.getTexCoordCount() + " tex coords"));
        node.add(new DefaultMutableTreeNode(mesh.getTriangleCount() + " triangles"));
        root.add(node);
      }
      tree.removeAll();
      DefaultTreeModel model = new DefaultTreeModel(root);
      tree.setModel(model);
    }

    public final void log(String info) {
      txtArea.append(info + "\n");
    }
  }

  private class RenderDialog extends JDialog {
    private static final long serialVersionUID = -8582492154611863437L;

    private ResultImagePanel resPanel;
    private BufferedImage resImage;
    private Renderer[] renderers = {new PathTracer()};
    private Renderer selectedRenderer = renderers[0];
    private Filter[] filters = {new ConeFilter(1), new GaussianFilter()};
    private Filter selectedFilter = filters[0];

    public RenderDialog() {
      setTitle("Render Settings");
      add(resPanel = new ResultImagePanel(), BorderLayout.CENTER);
      add(new ContPanel(), BorderLayout.EAST);
      setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
      setVisible(false);
      setResizable(true);
      pack();
    }

    private class ResultImagePanel extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {
      private static final long serialVersionUID = 2436178488034586868L;

      private int oX, oY;
      private float paintWidth, paintHeight;

      private boolean isLButtonDown;
      private int pressX, pressY;

      public ResultImagePanel() {
        resImage = new BufferedImage(PANEL_WIDTH / 2, PANEL_HEIGHT / 2, BufferedImage.TYPE_INT_RGB);
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));

        oX = (PANEL_WIDTH - resImage.getWidth()) / 2;
        oY = (PANEL_HEIGHT - resImage.getHeight()) / 2;

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
        g.fillRect(0, 0, getWidth(), getHeight());
        g.drawImage(resImage, oX, oY, (int) paintWidth, (int) paintHeight, null);
        g.setColor(Color.RED);
        g.drawString("output", oX, oY + 10);
      }

      @Override
      public void mouseClicked(MouseEvent arg0) {
        if (arg0.getButton() == MouseEvent.BUTTON3) {
          oX = (getWidth() - resImage.getWidth()) / 2;
          oY = (getHeight() - resImage.getHeight()) / 2;
          paintWidth = resImage.getWidth();
          paintHeight = resImage.getHeight();
          repaint();
        }
      }

      @Override
      public void mouseEntered(MouseEvent arg0) {
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      }

      @Override
      public void mouseExited(MouseEvent arg0) {
      }

      @Override
      public void mousePressed(MouseEvent arg0) {
        if (arg0.getButton() == MouseEvent.BUTTON1) {
          isLButtonDown = true;
          pressX = arg0.getX();
          pressY = arg0.getY();
        }
      }

      @Override
      public void mouseReleased(MouseEvent arg0) {
        isLButtonDown = false;
      }

      @Override
      public void mouseDragged(MouseEvent arg0) {
        if (isLButtonDown) {
          int x = arg0.getX(), y = arg0.getY();
          oX += (x - pressX);
          oY += (y - pressY);
          pressX = x;
          pressY = y;
          repaint();
        }
      }

      @Override
      public void mouseMoved(MouseEvent arg0) {
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent arg0) {
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
      private int width = PANEL_WIDTH / 4, height = PANEL_HEIGHT;
      private JTextField tfStatus;
      private JButton bStartRendering, bSaveImg, bStop;
      private SliderPanel sSamples, sDepth, sKdTreeDepth, sTrianglePerNode;
      private JComboBox<Renderer> cRenders;
      private ColorChooserPanel colorChooser;
      private JTextField tfW, tfH;

      public ContPanel() {
        setPreferredSize(new Dimension(width, height));
        setLayout(new FlowLayout());
        setBorder(new TitledBorder("Rendering parameters"));

        bStartRendering = new JButton("Let's roll");
        bStartRendering.setBackground(Color.YELLOW);
        bStop = new JButton("Stop Rendering");
        bStop.setEnabled(false);
        bSaveImg = new JButton("Save Image");
        tfStatus = new JTextField(16);
        tfStatus.setEditable(false);
        tfW = new JTextField(String.valueOf(PANEL_WIDTH / 2));
        tfH = new JTextField(String.valueOf(PANEL_HEIGHT / 2));
        cRenders = new JComboBox<Renderer>(renderers);
        cRenders.setSelectedIndex(0);
        sSamples = new SliderPanel(1, 100, 1, 1, "Samples^2 per pixel");
        sDepth = new SliderPanel(1, 25, 1, 1, "Tracing depth");
        sKdTreeDepth = new SliderPanel(5, 70, 20, 1, "Max Kd-tree depth");
        sTrianglePerNode = new SliderPanel(1, 10, 3, 1, "Max triangles per leaf");
        bStartRendering.addActionListener(this);
        bStop.addActionListener(this);
        bSaveImg.addActionListener(this);
        colorChooser = new ColorChooserPanel();
        add(new JLabel("Image width"));
        add(tfW);
        add(new JLabel("Image height"));
        add(tfH);
        add(sKdTreeDepth);
        add(sTrianglePerNode);
        add(new JLabel("Select renderer"));
        add(cRenders);
        add(sSamples);
        add(sDepth);
        add(new JLabel("Select background color"));
        add(colorChooser);
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
        int w = Integer.parseInt(tfW.getText()), h = Integer.parseInt(tfH.getText());
        int depth = (int) sDepth.getValue(), samples = (int) sSamples.getValue();
        if (o == bStop) {
          camera.stopRendering();
        } else if (o == bStartRendering) {
          selectedRenderer = (Renderer) cRenders.getSelectedItem();
          selectedRenderer.setFilter(selectedFilter);
          camera.setRenderer(selectedRenderer);
          camera.setBackgroundColor(colorChooser.getSelectedColor());
          sceneGraph.getSky().setColor(colorChooser.getSelectedColor());
          int bspDepth = (int) sKdTreeDepth.getValue();
          sceneGraph.setMaxKdTreeDepth(bspDepth);
          int maxTrianlgesPerNode = (int) sTrianglePerNode.getValue();
          sceneGraph.setMaxTrisPerLeaf(maxTrianlgesPerNode);
          selectedRenderer.setBouncingDepth(depth);
          camera.setSamplesPerPixel(samples);
          resImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
          new RenderingThread(w, h).start();
        } else if (o == bSaveImg) {
          JFileChooser chooser = new JFileChooser();
          FileNameExtensionFilter filter = new FileNameExtensionFilter("imgae file *.jpg *.bmp *.png *.gif", "png",
              "jpg", "jpeg", "bmp", "gif");
          chooser.setFileFilter(filter);
          int ret = chooser.showSaveDialog(renderDialog);
          if (ret == JFileChooser.APPROVE_OPTION) {
            try {
              File file = chooser.getSelectedFile();
              String extName = file.getName();
              extName = extName.substring(extName.lastIndexOf('.') + 1);
              ImageIO.write(resImage, extName, file);
              JOptionPane.showMessageDialog(renderDialog, "saved to  " + file.getAbsolutePath());
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
          int oriW = camera.getWidth(), oriH = camera.getHeight();
          camera.setScreenSize(w, h);
          bStartRendering.setEnabled(false);
          bStop.setEnabled(true);
          tfStatus.setText("Working...");

          log("--rendering started--");
          log("With " + Runtime.getRuntime().availableProcessors() + " threads.");
          log(sceneGraph.getTrianglesNum() + " triangles in total.");

          long t = System.nanoTime();
          camera.renderSync(resImage, resPanel);
          t = System.nanoTime() - t;
          int seconds = (int) (t / 1000000000);
          int hours = seconds / 3600;
          int mins = (seconds - hours * 3600) / 60;
          seconds = seconds - hours * 3600 - mins * 60;
          String time = String.format("%d hrs %d mins %d secs.", hours, mins, seconds);
          tfStatus.setText("Done-- " + time);

          log("time elapsed : " + time);
          log("--rendering finished--");
          bStartRendering.setEnabled(true);
          bStop.setEnabled(false);
          camera.setScreenSize(oriW, oriH);
          setViewPanelEnabled(true);
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
  // Color c = new Color();
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

  private class MaterialEditorDialog extends SimpleDialog {
    private static final long serialVersionUID = -1500497763671474467L;

    private Material currentMaterial;
    // private BufferedImage texImg;

    private ColorChooserPanel emittancePanel, reflectancePanel, transmissionPanel, absorptionPanel;
    private JTextField tfName;
    private JButton bSave, bClose, bNewMat, bAssign;
    private SliderPanel spRefrInd;
    private JComboBox<Material> cbMaterialList;
    private JComboBox<String> cbIsLight;
    private String[] isLights = {"Yes", "No"};

    public MaterialEditorDialog(int w, int h) {
      super(w, h);
      setTitle("Material Editor");
      panel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
      panel.add(new JLabel("Material Name"));
      panel.add(tfName = new JTextField(8));
      panel.add(new JLabel("Emittance Color"));
      panel.add(emittancePanel = new ColorChooserPanel());
      panel.add(new JLabel("Reflection Color"));
      panel.add(reflectancePanel = new ColorChooserPanel());
      panel.add(new JLabel("Transmission Color"));
      panel.add(transmissionPanel = new ColorChooserPanel());
      panel.add(new JLabel("Absorption Color"));
      panel.add(absorptionPanel = new ColorChooserPanel());
      panel.add(spRefrInd = new SliderPanel(100, 350, 100, 100, "Refractive Index"));

      bSave = new JButton("Save");
      bSave.addActionListener(new ButtonRes());
      bNewMat = new JButton("New Material");
      bNewMat.addActionListener(new ButtonRes());
      bClose = new JButton("Close");
      bClose.addActionListener(new ButtonRes());
      cbMaterialList = new JComboBox<Material>();
      for (Material m : materialList) {
        cbMaterialList.addItem(m);
      }
      cbMaterialList.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          int i = cbMaterialList.getSelectedIndex();
          if (i != -1) {
            loadMaterial(materialList.get(i));
          }
        }
      });
      panel.add(new JLabel("Is it a light source?"));
      cbIsLight = new JComboBox<String>(isLights);
      bAssign = new JButton("Assign to selected mesh");
      bAssign.addActionListener(new ButtonRes());
      panel.add(cbIsLight);
      panel.add(bNewMat);
      panel.add(bSave);
      panel.add(bAssign);
      panel.add(bClose);
      panel.add(cbMaterialList);
      addComponentListener(new ComponentAdapter() {
        public void componentShown(ComponentEvent e) {
          if (selectedMesh != null) {
            bAssign.setEnabled(true);
            Material m = selectedMesh.getMaterial();
            cbMaterialList.setSelectedItem(m);
          } else {
            bAssign.setEnabled(false);
            cbMaterialList.setSelectedIndex(0);
          }
        }
      });
      pack();
    }

    private void setMaterialBySliders(Material material) {
      material.setName(tfName.getText());
      material.setEmittance(emittancePanel.getSelectedColor());
      material.setReflectance(reflectancePanel.getSelectedColor());
      material.setTransmission(transmissionPanel.getSelectedColor());
      material.setAbsorption(absorptionPanel.getSelectedColor());
      material.setRefractiveIndex(spRefrInd.getValue());
      material.setIsLight(cbIsLight.getSelectedIndex() == 0 ? true : false);
    }

    private class ButtonRes implements ActionListener {
      public void actionPerformed(ActionEvent ae) {
        Object o = ae.getSource();
        if (o == bSave) {
          setMaterialBySliders(currentMaterial);
        } else if (o == bClose) {
          MaterialEditorDialog.this.setVisible(false);
        } else if (o == bNewMat) {
          Material m = new IdealDiffuseModel();
          m.setName("BSDF_" + materialList.size());
          materialList.add(m);
          cbMaterialList.addItem(m);
          cbMaterialList.setSelectedItem(m);
        } else if (o == bAssign) {
          if (selectedMesh != null) {
            selectedMesh.setMaterial(currentMaterial);
          }
        }
      }
    }

    private void loadMaterial(Material mat) {
      this.currentMaterial = mat;
      displayMaterial();
    }

    private void displayMaterial() {
      if (currentMaterial == null) {
        return;
      }
      tfName.setText("" + currentMaterial.getName());
      emittancePanel.setSelectedColor(currentMaterial.getEmittance());
      reflectancePanel.setSelectedColor(currentMaterial.getReflectance());
      transmissionPanel.setSelectedColor(currentMaterial.getTransmission());
      absorptionPanel.setSelectedColor(currentMaterial.getAbsorption());
      spRefrInd.setValue(currentMaterial.getRefractiveIndex());
      cbIsLight.setSelectedIndex(currentMaterial.isLight() ? 0 : 1);
    }
  }

  private class ColorChooserPanel extends JPanel {
    private static final long serialVersionUID = 1889527783733521153L;
    private Color color;

    public ColorChooserPanel() {
      setPreferredSize(new Dimension(100, 50));
      addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          color = JColorChooser.showDialog(materialEditDialog, "Choose color", Color.WHITE);
          repaint();
        }
      });
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      g.setColor(color);
      g.fillRect(0, 0, getWidth(), getHeight());
    }

    public void setSelectedColor(Color3f c) {
      color = new Color(c.r, c.g, c.b);
      repaint();
    }

    public Color3f getSelectedColor() {
      if (color == null) {
        return new Color3f(1.0f, 1.0f, 1.0f);
      }
      return new Color3f(color.getRed() / 256.0f, color.getGreen() / 256.0f, color.getBlue() / 256.0f);
    }
  }

  private class ButtonPanel extends JPanel {
    private static final long serialVersionUID = 639206566435527693L;

    private int width = PANEL_WIDTH;
    private int height = 54;

    private JButton bRenderDia, bPointLight, bDirectionalLight, bSpotLight, bEditMat, bEditTex, bNewScene, bSaveScene,
        bLoadScene, bImptModel;

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
      bEditMat.setToolTipText("Material Editor");
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
      bImptModel = new JButton(new ImageIcon("importButton.png"));
      bImptModel.addActionListener(new ActionRes());
      bImptModel.setToolTipText("Import Model From File");
      hBox.add(bImptModel);
      hBox.add(bEditMat);
      hBox.add(bRenderDia);
      hBox.add(bSaveScene);
      hBox.add(bLoadScene);

      add(hBox, BorderLayout.CENTER);
    }

    protected void paintComponent(Graphics g) {
      super.paintComponents(g);
      Graphics2D g2d = (Graphics2D) g;
      g2d.setPaint(new GradientPaint(0, 0, Color.YELLOW, 0, getHeight(), Color.ORANGE));
      g2d.fillRect(0, 0, getWidth(), getHeight());
    }

    private class ActionRes implements ActionListener {
      public void actionPerformed(ActionEvent ae) {
        Object o = ae.getSource();
        if (o == bRenderDia) {
          renderDialog.setVisible(true);
        } else if (o == bEditMat) {
          materialEditDialog.setVisible(true);
        } else if (o == bImptModel) {
          JFileChooser chooser = new JFileChooser();
          FileNameExtensionFilter filter = new FileNameExtensionFilter("model file *.obj", "obj");
          chooser.setFileFilter(filter);
          int ret = chooser.showOpenDialog(null);
          if (ret == JFileChooser.APPROVE_OPTION) {
            try {
              final File file = chooser.getSelectedFile();
              Executors.newSingleThreadExecutor().execute(new Runnable() {
                @Override
                public void run() {
                  try {
                    for (TriangleMesh mesh : loader.loadModelFile(file)) {
                      mesh.setMaterial(materialList.get(0));
                      sceneGraph.addMesh(mesh);
                      meshesList.add(mesh);
                    }
                    sceneGraph.setMaxKdTreeDepth(10);
                    sceneGraph.finish();
                    viewPanel.display();
                    westPanel.updateModelTree();
                  } catch (Exception e) {
                    e.printStackTrace();
                  }
                }
              });
            } catch (Exception ioe) {
              ioe.printStackTrace();
            }
          }
        } else if (o == bSaveScene) {
          JFileChooser chooser = new JFileChooser();
          FileNameExtensionFilter filter = new FileNameExtensionFilter("Scene file *.sce", "sce");
          chooser.setFileFilter(filter);
          int ret = chooser.showSaveDialog(null);
          if (ret == JFileChooser.APPROVE_OPTION) {
            try {
              File file = chooser.getSelectedFile();
              ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
              oos.writeObject(sceneGraph);
              JOptionPane.showMessageDialog(null, "saved to  " + file.getAbsolutePath(), "Save scence",
                  JOptionPane.OK_OPTION);
              oos.close();
            } catch (Exception ioe) {
              ioe.printStackTrace();
            }
          }
        } else if (o == bLoadScene) {
          JFileChooser chooser = new JFileChooser();
          FileNameExtensionFilter filter = new FileNameExtensionFilter("Scene File *.sce", "sce");
          chooser.setFileFilter(filter);
          int ret = chooser.showOpenDialog(null);
          if (ret == JFileChooser.APPROVE_OPTION) {
            try {
              File file = chooser.getSelectedFile();
              ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
              sceneGraph = (Scene) ois.readObject();
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

  private class ViewPanel extends GLJPanel implements GLEventListener, MouseListener, MouseMotionListener,
      MouseWheelListener, KeyListener {
    private static final long serialVersionUID = 3635297711293484185L;
    private Camera cam;

    private GL2 gl;
    private GLU glu;

    private boolean isEnabled = true;

    public ViewPanel(Camera cam) {
      super();
      addGLEventListener(this);
      addMouseListener(this);
      addMouseMotionListener(this);
      addMouseWheelListener(this);
      addKeyListener(this);
      setPreferredSize(new Dimension(MainGui.PANEL_WIDTH, MainGui.PANEL_HEIGHT));
      this.cam = cam;
      addFocusListener(new FocusListener() {
        public void focusGained(FocusEvent e) {
          display();
        }

        public void focusLost(FocusEvent e) {
          display();
        }
      });
      addComponentListener(new ComponentAdapter() {
        public void componentResized(ComponentEvent e) {
          Dimension d = getSize();
          ViewPanel.this.cam.setScreenSize(d.width, d.height);
        }
      });
      resetCamera();
    }

    public void setEnabled(boolean isEnabled) {
      this.isEnabled = isEnabled;
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      g.setColor(Color.RED);
      g.drawString("perspective", 2, 12);
    }

    public void resetCamera() {
      cam.lookAt(new Vec3f(30, 0, 30), new Vec3f(0, 0, 0), new Vec3f(0, 1, 0));
    }

    private void drawGrids(float d, GL2 gl) {
      int n = 8;
      float x, z, beg = -d * n, end = d * n;
      LHCoordinateSystem coSys = cam.getCoordinateSystem();
      Vec3f v0 = new Vec3f(), v1 = new Vec3f();
      gl.glDisable(GL2.GL_LIGHTING);
      gl.glBegin(GL2.GL_LINES);
      gl.glColor3f(0.4f, 0.4f, 0.4f);
      x = beg;
      n = (n << 1);
      for (int i = 0; i <= n; i++) {
        v0 = new Vec3f(x, 0, beg);
        v1 = new Vec3f(x, 0, end);
        v0 = coSys.transPointToLocal(v0);
        v1 = coSys.transPointToLocal(v1);
        gl.glVertex3f(v0.x, v0.y, v0.z);
        gl.glVertex3f(v1.x, v1.y, v1.z);
        x += d;
      }
      z = beg;
      for (int i = 0; i <= n; i++) {
        v0 = new Vec3f(beg, 0, z);
        v1 = new Vec3f(end, 0, z);
        v0 = coSys.transPointToLocal(v0);
        v1 = coSys.transPointToLocal(v1);
        gl.glVertex3f(v0.x, v0.y, v0.z);
        gl.glVertex3f(v1.x, v1.y, v1.z);
        z += d;
      }
      gl.glEnd();
      gl.glEnable(GL2.GL_LIGHTING);
    }

    private int button, key;
    private int oldX, oldY;
    private Vec3f dv = new Vec3f(), n = new Vec3f();
    private Vec3f v1 = new Vec3f(), v2 = new Vec3f();
    private Cursor moveCursor = new Cursor(Cursor.MOVE_CURSOR);

    public void mouseWheelMoved(MouseWheelEvent e) {
      if (!isEnabled) {
        return;
      }
      setCursor(new Cursor(Cursor.N_RESIZE_CURSOR));
      int ticks = e.getWheelRotation();
      cam.translate(new Vec3f(0, 0, -ticks * 3));
      display();
    }

    public void mouseClicked(MouseEvent e) {
      if (!isEnabled) {
        return;
      }
      switch (button) {
        case MouseEvent.BUTTON1:
          selectedMesh = cam.pick(e.getX(), e.getY());
          onMeshSelected();
          break;

        case MouseEvent.BUTTON3:
          break;

        default:
          break;
      }
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
      v2 = cam.getLocalPointFromScreen(e.getX(), e.getY());
      v1 = cam.getLocalPointFromScreen(oldX, oldY);
      v2.z = cam.getZNear();
      v1.z = cam.getZNear();
      dv = sub(v1, v2).normalize();
      switch (button) {
        case MouseEvent.BUTTON1:
          break;

        case MouseEvent.BUTTON2:
          setCursor(moveCursor);
          cam.translate(dv);
          display();
          oldX = e.getX();
          oldY = e.getY();
          break;

        case MouseEvent.BUTTON3:
          v1 = v1.normalize();
          v2 = v2.normalize();
          float angle = (float) (3 * Math.acos(dot(v1, v2)) * 180 / Math.PI);
          n = cross(v1, v2).normalize();
          cam.rotate(n, angle);
          display();
          oldX = e.getX();
          oldY = e.getY();
          break;

        default:
          break;
      }
    }

    public void mouseMoved(MouseEvent e) {
      setCursor(Cursor.getDefaultCursor());
    }

    public void keyPressed(KeyEvent e) {
      if (!isEnabled) {
        return;
      }
      key = e.getKeyCode();
      switch (key) {
        case KeyEvent.VK_R:
          resetCamera();
          display();
          break;
        default:
          break;
      }
    }

    public void keyReleased(KeyEvent e) {
      if (!isEnabled) {
        return;
      }
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
     * @param drawable
     * @param arg1
     * @param arg2
     */
    @SuppressWarnings("unused")
    public void displayChanged(GLAutoDrawable drawable, boolean arg1, boolean arg2) {
    }

    public void init(GLAutoDrawable drawable) {
      gl = drawable.getGL().getGL2();
      glu = new GLU();
      gl.glShadeModel(GL2.GL_SMOOTH);
      gl.glEnable(GL2.GL_DEPTH_TEST);
      gl.glCullFace(GL2.GL_NONE);
      gl.glClearColor(0.54f, 0.68f, 0.78f, 1.0f);

      gl.glLightModeli(GL2.GL_LIGHT0, GL2.GL_LIGHT_MODEL_TWO_SIDE);
      gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, new float[]{1f, 1f, 1f, 1f}, 0);
      gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, new float[]{0f, 0f, 0f, 1f}, 0);
      gl.glEnable(GL2.GL_LIGHT0);

      gl.glEnable(GL2.GL_LIGHTING);
    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
      cam.setScreenSize(w, h);
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
    }
  }

  private class SliderPanel extends JPanel {
    private static final long serialVersionUID = 5246386435723037503L;
    private JLabel label;
    private String str;
    JSlider s;
    private float divisor;

    public SliderPanel(int min, int max, int init, float divisor, String labl) {
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

  public void log(final String log) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        westPanel.log(log);
      }
    });
  }

  public void setViewPanelEnabled(boolean isEnabled) {
    viewPanel.setEnabled(isEnabled);
  }

  private void onMeshSelected() {
    if (selectedMesh != null) {
      materialEditDialog.loadMaterial(selectedMesh.getMaterial());
    }
    viewPanel.display();
  }
}