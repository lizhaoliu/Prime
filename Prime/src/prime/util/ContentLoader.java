package prime.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import prime.gui.MainGUI;
import prime.math.Vec3f;
import prime.math.Vec3i;
import prime.model.TriangleMesh;

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
    
    private MainGUI mainGUI;

    public ContentLoader(MainGUI mainGUI) {
	this.mainGUI = mainGUI;
    }
    
    /**
     * 
     * @param file input model file
     * @return a {@link Collection} of loaded {@link TriangleMesh}
     * @throws Exception
     */
    public Collection<TriangleMesh> loadModelFile(File file) throws Exception {
	mainGUI.addSystemLog("--importing from '" + file.getName() + "'.--");
	Collection<TriangleMesh> result = null;
	String extName = file.getName();
	extName = extName.substring(extName.lastIndexOf('.') + 1);
	if (extName.equalsIgnoreCase("obj")) {
	    result = loadObjFile(file);
	    mainGUI.addSystemLog("--importing succeeded--");
	    return result;
	} else {
	    mainGUI.addSystemLog("--importing failed --");
	    return result;
	}
    }

    /**
     * the main method in charge of loading a model from OBJ file format
     * 
     * @param file
     * @return
     * @throws IOException
     */
//    private TriangleMesh[] loadOBJFile(File file) throws IOException {
//	List<Vec3f> vList = Lists.newArrayList();
//	List<Vec3f> nList = Lists.newArrayList();
//	List<Vec3f> tList = Lists.newArrayList();
//
//	BufferedReader reader = new BufferedReader(new FileReader(file));
//	String line;
//	int nMeshes = 0;
//	while ((line = reader.readLine()) != null) {
//	    if (line.length() < 1) {
//		continue;
//	    }
//	    char buf0 = line.charAt(0);
//	    if (buf0 == 'g') {
//		if (line.length() > 1) {
//		    ++nMeshes;
//		}
//	    } else if (buf0 == 'v') {
//		String[] ss = line.split(" ");
//		int len = ss.length;
//		Vec3f v = new Vec3f(Float.parseFloat(ss[len - 3]),
//			Float.parseFloat(ss[len - 2]),
//			Float.parseFloat(ss[len - 1]));
//		char buf1 = line.charAt(1);
//		if (buf1 == ' ') {
//		    vList.add(v);
//		} else if (buf1 == 'n') {
//		    nList.add(v);
//		} else if (buf1 == 't') {
//		    tList.add(v);
//		}
//	    }
//	}
//	reader.close();
//	mainGUI.addSystemLog(vList.size() + " vertices are loaded.");
//	mainGUI.addSystemLog(nList.size() + " normals are loaded.");
//	mainGUI.addSystemLog(tList.size() + " texture coordinates are loaded.");
//
//	if (nMeshes < 1) {
//	    nMeshes = 1;
//	}
//	TriangleMesh[] result = new TriangleMesh[nMeshes];
//	for (int i = 0; i < result.length; i++) {
//	    result[i] = new TriangleMesh();
//	    result[i].setSharedVertexList(vList);
//	    result[i].setSharedNormalList(nList);
//	    result[i].setSharedTexCoordList(tList);
//	}
//	mainGUI.addSystemLog(nMeshes + " triangle meshes are loaded.");
//
//	int iMeshes = -1;
//	reader = new BufferedReader(new FileReader(file));
//	while ((line = reader.readLine()) != null) {
//	    if (line.length() < 1) {
//		continue;
//	    }
//	    char buf0 = line.charAt(0);
//	    if (buf0 == 'g' && line.length() > 1) {
//		String[] ss = line.split(" ");
//		result[++iMeshes].setName(ss[1]);
//	    } else if (buf0 == 'f' && line.charAt(1) == ' ') {
//		if (iMeshes == -1) {
//		    iMeshes = 0;
//		}
//		String[] ss = line.split(" ");
//		int segLen = ss[1].split("/").length; // cuz ss[0] == "f"
//		String[][] data = new String[ss.length - 1][segLen];
//		for (int i = 0; i < ss.length - 1; i++) {
//		    data[i] = ss[i + 1].split("/");
//		}
//		int caseID;
//		if (data[0].length == 1) {
//		    caseID = 0; // %d
//		} else {
//		    if (data[0][1].length() == 0) {
//			caseID = 1; // %d//%d
//		    } else {
//			caseID = 2; // %d/%d/%d
//		    }
//		}
//		int i0, i1, i2;
//		for (int i = 0; i < data.length - 2; i++) // ss[0] = "f"
//		{
//		    switch (caseID) {
//		    case 0: // %d
//			i0 = Integer.parseInt(data[i][0]) - 1;
//			i1 = Integer.parseInt(data[i + 1][0]) - 1;
//			i2 = Integer.parseInt(data[i + 2][0]) - 1;
//			result[iMeshes].addVertexIndex(new Vec3i(i0, i1, i2));
//			break;
//
//		    case 1: // %d//%d
//			i0 = Integer.parseInt(data[i][0]) - 1;
//			i1 = Integer.parseInt(data[i + 1][0]) - 1;
//			i2 = Integer.parseInt(data[i + 2][0]) - 1;
//			result[iMeshes].addVertexIndex(new Vec3i(i0, i1, i2));
//
//			i0 = Integer.parseInt(data[i][2]) - 1;
//			i1 = Integer.parseInt(data[i + 1][2]) - 1;
//			i2 = Integer.parseInt(data[i + 2][2]) - 1;
//			result[iMeshes].addNormalIndex(new Vec3i(i0, i1, i2));
//			break;
//
//		    default:// %d/%d/%d
//			i0 = Integer.parseInt(data[i][0]) - 1;
//			i1 = Integer.parseInt(data[i + 1][0]) - 1;
//			i2 = Integer.parseInt(data[i + 2][0]) - 1;
//			result[iMeshes].addVertexIndex(new Vec3i(i0, i1, i2));
//
//			i0 = Integer.parseInt(data[i][1]) - 1;
//			i1 = Integer.parseInt(data[i + 1][1]) - 1;
//			i2 = Integer.parseInt(data[i + 2][1]) - 1;
//			result[iMeshes].addTexCoordIndex(new Vec3i(i0, i1, i2));
//
//			i0 = Integer.parseInt(data[i][2]) - 1;
//			i1 = Integer.parseInt(data[i + 1][2]) - 1;
//			i2 = Integer.parseInt(data[i + 2][2]) - 1;
//			result[iMeshes].addNormalIndex(new Vec3i(i0, i1, i2));
//		    }
//		}
//		;
//	    }
//	}
//	reader.close();
//
//	return result;
//    }
    
    /**
     * 
     * @param file input model file
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
	    case "o":	//starting point of a new mesh
		//dump all data into a new mesh
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
		    //logging
		    mainGUI.addSystemLog(vList.size() + " vertices loaded.");
		    mainGUI.addSystemLog(nList.size() + " normals loaded.");
		    mainGUI.addSystemLog(tList.size() + " tex coords loaded.");
		    mainGUI.addSystemLog(viList.size() + " triangles loaded.");
		    mainGUI.addSystemLog("--Finished loading mesh: " + meshName);
		}
		//reset for reading new data
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
		mainGUI.addSystemLog("--Started loading mesh: " + meshName);
		break;
		
	    case "v":
		vList.add(new Vec3f(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3])));
		break;
		
	    case "vt":
		tList.add(new Vec3f(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3])));
		break;
		
	    case "vn":
		nList.add(new Vec3f(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3])));
		break;
		
	    case "f":
		//determine face type: {v, v/t, v//n, v/t/n}
		if (faceFormat == ObjFaceFormat.UNKNOWN) {
		    String[] segs = tokens[1].split("/");
		    if (segs.length == 1) {	//v
			faceFormat = ObjFaceFormat.VERTEX;
		    }
		    else if (segs.length == 2) {	//v/t
			faceFormat = ObjFaceFormat.VERTEX_TEXCOORD;
		    }
		    else {	//v/t?/n
			if (segs[1].isEmpty()) {	//v//n
			    faceFormat = ObjFaceFormat.VERTEX_NORMAL;
			} 
			else {	//v/t/n
			    faceFormat = ObjFaceFormat.VERTEX_TEXCOORD_NORMAL;
			}
		    }
		}
		//read face data
		String[] segs1 = tokens[1].split(SLASH); 
		for (int i = 2; i < tokens.length - 1; ++i) {
		    String[] segs2 = tokens[i].split(SLASH), segs3 = tokens[i + 1].split(SLASH); 
		    switch (faceFormat) {
		    case VERTEX:
		    case VERTEX_TEXCOORD:
			throw new Exception("TriangleMesh " + meshName + " does not have normal vectors.");
		    case VERTEX_TEXCOORD_NORMAL:
			tiList.add(new Vec3i(Integer.parseInt(segs1[1]) - numVt - 1, 
				Integer.parseInt(segs2[1]) - numVt - 1, 
				Integer.parseInt(segs3[1]) - numVt - 1));
		    case VERTEX_NORMAL:
			viList.add(new Vec3i(Integer.parseInt(segs1[0]) - numV - 1, 
				Integer.parseInt(segs2[0]) - numV - 1, 
				Integer.parseInt(segs3[0]) - numV - 1));
			niList.add(new Vec3i(Integer.parseInt(segs1[2]) - numVn - 1, 
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
	
	//dump the last triangle mesh
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
	//logging
	mainGUI.addSystemLog(vList.size() + " vertices loaded.");
	mainGUI.addSystemLog(nList.size() + " normals loaded.");
	mainGUI.addSystemLog(tList.size() + " tex coords loaded.");
	mainGUI.addSystemLog(viList.size() + " triangles loaded.");
	mainGUI.addSystemLog("--Finished loading mesh: " + meshName);
	
	return ret;
    }
}
