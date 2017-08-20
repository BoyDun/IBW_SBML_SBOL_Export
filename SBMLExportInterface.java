package roadblock.ibw.exports.sbml;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.xtext.resource.XtextResource;
import org.sbml.jsbml.SBMLWriter;

import roadblock.caching.ModelCache;
import roadblock.dataprocessing.export.SBML_Export;
import roadblock.emf.ibl.Ibl.FlatModel;
import roadblock.emf.ibl.Ibl.Model;
import roadblock.resource.IblResourceObservable;

public class SBMLExportInterface {

	/**
	 * Appends a backslash to the path name if not present.
	 * 
	 * @param path
	 * @return the fixed path
	 */
	private static String fixPath(String path) {
		if (path.charAt(path.length() - 1) == '/')
			return path;
		else
			return (path + "/");
	}

	/**
	 * Appends the ".xml" extension onto a file name if not present.
	 * 
	 * @param name
	 * @return the fixed name
	 */
	private static String fixName(String name) {
		if (name.length() > 3 && name.substring(name.length() - 4).equals(".xml"))
			return name;
		return name + ".xml";
	}

	/**
	 * Interfaces between the export wizard and conversion functionality. Fetches
	 * the proper IBLResource if not present, and converts either the hierarchical
	 * or flattened EMF model depending on the user's choice. The created SBML
	 * document then gets saved to the specified path and file name.
	 * 
	 * @param path
	 * @param name
	 * @param toFlatten
	 * @return
	 */
	public static boolean convertSBML(String path, String name, boolean toFlatten) {
		if (IblResourceObservable.getInstance().getCurrentIblResource() == null) {
			IWorkbench wb = PlatformUI.getWorkbench();
			IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
			IWorkbenchPage page = win.getActivePage();
			IblResourceObservable.getInstance().partVisible(page.getActivePartReference());
		}
		if (IblResourceObservable.getInstance().getCurrentIblResource() != null) {
			XtextResource currentIblResource = IblResourceObservable.getInstance().getCurrentIblResource();
			try {
				SBMLWriter writer = new SBMLWriter();
				if (toFlatten) {
					FlatModel emfFlatModel = ModelCache.getInstance().getFlatModel(currentIblResource);
					writer.writeSBMLToFile(SBML_Export.makeSBMLDocument(null, emfFlatModel),
							fixPath(path) + fixName(name));
				} else {
					Model emfModel = ModelCache.getInstance().getModel(currentIblResource);
					writer.writeSBMLToFile(SBML_Export.makeSBMLDocument(emfModel, null), fixPath(path) + fixName(name));
				}
			} catch (Exception e) {
				System.out.println("Exception caught:" + e);
				return false;
			}
			return true;
		}
		return false;
	}

}
