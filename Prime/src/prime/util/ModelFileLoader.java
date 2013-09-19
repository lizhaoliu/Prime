package prime.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


import prime.gui.MainGUI;
import prime.math.Tuple3;
import prime.math.Vector;
import prime.model.TriangleMesh;

/**
 * a model loader
 * @author lizhaoliu
 *
 */
public final class ModelFileLoader {
    private MainGUI mainGUI;

    public ModelFileLoader(MainGUI mainGUI) {
	this.mainGUI = mainGUI;
    }

    public final TriangleMesh[] load(File file) throws IOException {
	mainGUI.addSystemLog("--importing from '" + file.getName() + "'.--");
	TriangleMesh[] result = null;
	String extName = file.getName();
	extName = extName.substring(extName.lastIndexOf('.') + 1);
	if (extName.equalsIgnoreCase("obj")) {
	    result = loadOBJFile(file);
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
    private final TriangleMesh[] loadOBJFile(File file) throws IOException {
	List<Vector> vList = new ArrayList<Vector>();
	List<Vector> nList = new ArrayList<Vector>();
	List<Vector> tList = new ArrayList<Vector>();

	BufferedReader reader = new BufferedReader(new FileReader(file));
	String line;
	int nMeshes = 0;
	while ((line = reader.readLine()) != null) {
	    if (line.length() < 1) {
		continue;
	    }
	    char buf0 = line.charAt(0);
	    if (buf0 == 'g') {
		if (line.length() > 1) {
		    ++nMeshes;
		}
	    } else if (buf0 == 'v') {
		String[] ss = line.split(" ");
		int len = ss.length;
		Vector v = new Vector(Float.parseFloat(ss[len - 3]), Float
			.parseFloat(ss[len - 2]), Float.parseFloat(ss[len - 1]));
		char buf1 = line.charAt(1);
		if (buf1 == ' ') // vertex
		{
		    vList.add(v);
		} else if (buf1 == 'n') // normal
		{
		    nList.add(v);
		} else if (buf1 == 't') // texture coordinate
		{
		    tList.add(v);
		} else {
		}
	    }
	}
	reader.close();
	mainGUI.addSystemLog(vList.size() + " vertices are loaded.");
	mainGUI.addSystemLog(nList.size() + " normals are loaded.");
	mainGUI.addSystemLog(tList.size() + " texture coordinates are loaded.");

	if (nMeshes < 1) {
	    nMeshes = 1;
	}
	TriangleMesh[] result = new TriangleMesh[nMeshes];
	for (int i = 0; i < result.length; i++) {
	    result[i] = new TriangleMesh();
	    result[i].setSharedVertexList(vList);
	    result[i].setSharedNormalList(nList);
	    result[i].setSharedTexCoordList(tList);
	}
	mainGUI.addSystemLog(nMeshes + " triangle meshes are loaded.");

	int iMeshes = -1;
	reader = new BufferedReader(new FileReader(file));
	while ((line = reader.readLine()) != null) {
	    if (line.length() < 1) {
		continue;
	    }
	    char buf0 = line.charAt(0);
	    if (buf0 == 'g' && line.length() > 1) {
		String[] ss = line.split(" ");
		result[++iMeshes].setName(ss[1]);
	    } else if (buf0 == 'f' && line.charAt(1) == ' ') {
		if (iMeshes == -1) {
		    iMeshes = 0;
		}
		String[] ss = line.split(" ");
		int segLen = ss[1].split("/").length; // cuz ss[0] == "f"
		String[][] data = new String[ss.length - 1][segLen];
		for (int i = 0; i < ss.length - 1; i++) {
		    data[i] = ss[i + 1].split("/");
		}
		int caseID;
		if (data[0].length == 1) {
		    caseID = 0; // %d
		} else {
		    if (data[0][1].length() == 0) {
			caseID = 1; // %d//%d
		    } else {
			caseID = 2; // %d/%d/%d
		    }
		}
		int i0, i1, i2;
		for (int i = 0; i < data.length - 2; i++) // ss[0] = "f"
		{
		    switch (caseID) {
		    case 0: // %d
			i0 = Integer.parseInt(data[i][0]) - 1;
			i1 = Integer.parseInt(data[i + 1][0]) - 1;
			i2 = Integer.parseInt(data[i + 2][0]) - 1;
			result[iMeshes].addVertexIndex(new Tuple3(i0, i1, i2));
			break;

		    case 1: // %d//%d
			i0 = Integer.parseInt(data[i][0]) - 1;
			i1 = Integer.parseInt(data[i + 1][0]) - 1;
			i2 = Integer.parseInt(data[i + 2][0]) - 1;
			result[iMeshes].addVertexIndex(new Tuple3(i0, i1, i2));

			i0 = Integer.parseInt(data[i][2]) - 1;
			i1 = Integer.parseInt(data[i + 1][2]) - 1;
			i2 = Integer.parseInt(data[i + 2][2]) - 1;
			result[iMeshes].addNormalIndex(new Tuple3(i0, i1, i2));
			break;

		    default:// %d/%d/%d
			i0 = Integer.parseInt(data[i][0]) - 1;
			i1 = Integer.parseInt(data[i + 1][0]) - 1;
			i2 = Integer.parseInt(data[i + 2][0]) - 1;
			result[iMeshes].addVertexIndex(new Tuple3(i0, i1, i2));

			i0 = Integer.parseInt(data[i][1]) - 1;
			i1 = Integer.parseInt(data[i + 1][1]) - 1;
			i2 = Integer.parseInt(data[i + 2][1]) - 1;
			result[iMeshes].addTexCoordIndex(new Tuple3(i0, i1, i2));

			i0 = Integer.parseInt(data[i][2]) - 1;
			i1 = Integer.parseInt(data[i + 1][2]) - 1;
			i2 = Integer.parseInt(data[i + 2][2]) - 1;
			result[iMeshes].addNormalIndex(new Tuple3(i0, i1, i2));
		    }
		}
		;
	    }
	}
	reader.close();

	return result;
    }
}
