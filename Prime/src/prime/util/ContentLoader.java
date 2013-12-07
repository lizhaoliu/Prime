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
     * Load {@link TriangleMesh}s from a file
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
	    mainGUI.addSystemLog("-- importing succeeded --");
	    return result;
	} else {
	    mainGUI.addSystemLog("Unsupported file format");
	    mainGUI.addSystemLog("-- importing failed --");
	    return result;
	}
    }

    /**
     * Load {@link TriangleMesh}s from OBJ format file
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
	    case "g":
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
		    mainGUI.addSystemLog("--Finished loading mesh: " + meshName + "\n");
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
	mainGUI.addSystemLog("--Finished loading mesh: " + meshName + "\n");
	
	return ret;
    }
}
