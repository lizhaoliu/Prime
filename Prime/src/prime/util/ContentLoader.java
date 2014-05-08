package prime.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;

import prime.gui.MainGui;
import prime.math.Vec3f;
import prime.math.Vec3i;
import prime.model.TriangleMesh;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Utility class for loading assets
 */
public class ContentLoader {
  private static final String SPACE = " ";
  private static final String SLASH = "/";

  private static enum ObjFaceFormat {
    UNKNOWN, VERTEX, VERTEX_TEXCOORD, VERTEX_NORMAL, VERTEX_TEXCOORD_NORMAL
  }

  private MainGui mainGui;

  public ContentLoader(MainGui mainGUI) {
    this.mainGui = mainGUI;
  }

  /**
   * Load a collection of {@link TriangleMesh} from a file
   * 
   * @param file
   *          input model file
   * @return a {@link Collection} of loaded {@link TriangleMesh}
   * @throws Exception
   */
  public Collection<TriangleMesh> loadModelFile(@Nonnull File file) throws Exception {
    Preconditions.checkNotNull(file);
    
    mainGui.log("--importing from '" + file.getName() + "'.--");
    Collection<TriangleMesh> result = null;
    String extName = file.getName();
    extName = extName.substring(extName.lastIndexOf('.') + 1);
    if (extName.equalsIgnoreCase("obj")) {
      result = loadObjFile(file);
      mainGui.log("-- importing succeeded --");
      return result;
    } else {
      mainGui.log("Unsupported file format");
      mainGui.log("-- importing failed --");
      return result;
    }
  }

  /**
   * Load a collection of {@link TriangleMesh} from OBJ format file
   * 
   * @param file
   *          input model file
   * @return a {@link Collection} of loaded {@link TriangleMesh}
   * @throws Exception
   */
  private Collection<TriangleMesh> loadObjFile(File file) throws Exception {
    List<TriangleMesh> ret = Lists.newArrayList();

    List<Vec3f> vList = Lists.newArrayList(), nList = Lists.newArrayList(), tList = Lists.newArrayList();
    List<Vec3i> viList = Lists.newArrayList(), niList = Lists.newArrayList(), tiList = Lists.newArrayList();
    TriangleMesh.Builder builder = null;
    BufferedReader reader = new BufferedReader(new FileReader(file));
    String meshName = StringUtils.EMPTY;
    String buf;
    int numV = 0, numVn = 0, numVt = 0;
    ObjFaceFormat faceFormat = ObjFaceFormat.UNKNOWN;
    while ((buf = reader.readLine()) != null) {
      String[] tokens = buf.split(SPACE);
      switch (tokens[0]) {
      case "g":
      case "o": // starting point of a new mesh
        // create a new mesh with data
        if (builder != null) {
          TriangleMesh triangleMesh = builder
              .withName(meshName)
              .withVertexList(vList)
              .withNormalList(nList)
              .withTexCoordList(tList)
              .withVertexIndexList(viList)
              .withNormalIndexList(niList)
              .withTexCoordIndexList(tiList)
              .build();
          ret.add(triangleMesh);

          mainGui.log(vList.size() + " vertices loaded.");
          mainGui.log(nList.size() + " normals loaded.");
          mainGui.log(tList.size() + " tex coords loaded.");
          mainGui.log(viList.size() + " triangles loaded.");
          mainGui.log("--Finished loading mesh: " + meshName + "\n");
        }

        // reset for reading new data
        numV += vList.size();
        numVn += nList.size();
        numVt += tList.size();
        vList.clear();
        nList.clear();
        tList.clear();
        viList.clear();
        niList.clear();
        tiList.clear();
        faceFormat = ObjFaceFormat.UNKNOWN;
        meshName = tokens[1];
        builder = new TriangleMesh.Builder();
        mainGui.log("--Started loading mesh: " + meshName);
        break;

      case "v":
        vList.add(buildVec3fFromStrings(tokens));
        break;

      case "vt":
        tList.add(buildVec3fFromStrings(tokens));
        break;

      case "vn":
        nList.add(buildVec3fFromStrings(tokens));
        break;

      case "f":
        // determine face type: {v, v/t, v//n, v/t/n}
        if (faceFormat == ObjFaceFormat.UNKNOWN) {
          String[] segs = tokens[1].split(SLASH);
          if (segs.length == 1) { // v
            faceFormat = ObjFaceFormat.VERTEX;
          } else if (segs.length == 2) { // v/t
            faceFormat = ObjFaceFormat.VERTEX_TEXCOORD;
          } else { // v/t?/n
            if (segs[1].isEmpty()) { // v//n
              faceFormat = ObjFaceFormat.VERTEX_NORMAL;
            } else { // v/t/n
              faceFormat = ObjFaceFormat.VERTEX_TEXCOORD_NORMAL;
            }
          }
        }

        // read face data
        String[] segs1 = tokens[1].split(SLASH);
        for (int i = 2; i < tokens.length - 1; ++i) {
          String[] segs2 = tokens[i].split(SLASH), segs3 = tokens[i + 1].split(SLASH);
          switch (faceFormat) {
          case VERTEX:
          case VERTEX_TEXCOORD:
            throw new Exception("TriangleMesh " + meshName + " does not have normal vectors.");
          case VERTEX_TEXCOORD_NORMAL:
            tiList.add(new Vec3i(
                Integer.parseInt(segs1[1]) - numVt - 1, 
                Integer.parseInt(segs2[1]) - numVt - 1,
                Integer.parseInt(segs3[1]) - numVt - 1));
          case VERTEX_NORMAL:
            viList.add(new Vec3i(
                Integer.parseInt(segs1[0]) - numV - 1,
                Integer.parseInt(segs2[0]) - numV - 1, 
                Integer.parseInt(segs3[0]) - numV - 1));
            niList.add(new Vec3i(
                Integer.parseInt(segs1[2]) - numVn - 1, 
                Integer.parseInt(segs2[2]) - numVn - 1,
                Integer.parseInt(segs3[2]) - numVn - 1));
            break;
          default:
            break;
          }
        }
        break;

      default:
        break;
      }
    }
    reader.close();

    // build the last triangle mesh
    if (builder != null) {
      TriangleMesh triangleMesh = builder
          .withName(meshName)
          .withVertexList(vList)
          .withNormalList(nList)
          .withTexCoordList(tList)
          .withVertexIndexList(viList)
          .withNormalIndexList(niList)
          .withTexCoordIndexList(tiList)
          .build();
      ret.add(triangleMesh);
    }
    
    mainGui.log(vList.size() + " vertices loaded.");
    mainGui.log(nList.size() + " normals loaded.");
    mainGui.log(tList.size() + " tex coords loaded.");
    mainGui.log((viList.size() - 2) + " triangles loaded.");
    mainGui.log("--Finished loading mesh: " + meshName + "\n");

    return ret;
  }

  private Vec3f buildVec3fFromStrings(String[] strs) {
    return new Vec3f(Float.parseFloat(strs[1]), Float.parseFloat(strs[2]), Float.parseFloat(strs[3]));
  }
}
