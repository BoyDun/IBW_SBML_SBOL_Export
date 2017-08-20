package roadblock.ibw.exports.sbol;

import java.io.FileOutputStream;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.xtext.resource.XtextResource;
import org.sbolstandard.core2.SBOLDocument;
import roadblock.biocompiler.Biocompiler;
import roadblock.caching.ModelCache;
import roadblock.dataprocessing.export.SBOL_Export;
import roadblock.emf.ibl.Ibl.Model;
import roadblock.resource.IblResourceObservable;

public class SBOLExportInterface {

	/**
	 * Verifies the user-submitted namespace is valid for SBOL
	 * 
	 * @param namespace
	 *            is the user-submitted namespace
	 * @return if the namespace is valid or not
	 */
	public static boolean verifyNamespace(String namespace) {
		try {
			new SBOLDocument().setDefaultURIprefix(namespace);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

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
			return path + '/';
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
	 * the proper IBLResource if not present. The created SBOL document includes
	 * information from the biocompiled model if the user wishes, and it gets saved
	 * to the specified path and file name.
	 * 
	 * @param path
	 * @param name
	 * @param namespace
	 * @param compile
	 * @return
	 */
	public static boolean convertSBOL(String path, String name, String namespace, boolean compile) {
		if (IblResourceObservable.getInstance().getCurrentIblResource() == null) {
			IWorkbench wb = PlatformUI.getWorkbench();
			IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
			IWorkbenchPage page = win.getActivePage();
			IblResourceObservable.getInstance().partVisible(page.getActivePartReference());
		}
		if (IblResourceObservable.getInstance().getCurrentIblResource() != null) {
			XtextResource currentIblResource = IblResourceObservable.getInstance().getCurrentIblResource();
			Model emfModel = ModelCache.getInstance().getModel(currentIblResource);
			try {
				SBOLDocument doc;
				if (!compile) {
					doc = SBOL_Export.makeSBOLDocument(emfModel, null, namespace);
				} else {
					Biocompiler biocompiler = new Biocompiler(emfModel);
					biocompiler.gatherParts();
					biocompiler.compile();
					doc = SBOL_Export.makeSBOLDocument(emfModel, biocompiler.biocompilerModel, namespace);
				}
				doc.write(new FileOutputStream(fixPath(path) + fixName(name)));
			} catch (Exception e) {
				System.out.println("Exception caught:" + e);
				return false;
			}
			return true;
		}
		return false;
	}

}
