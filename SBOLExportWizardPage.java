package roadblock.ibw.exports.sbol;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.internal.wizards.datatransfer.WizardFileSystemResourceExportPage1;

@SuppressWarnings("restriction")
public class SBOLExportWizardPage extends WizardFileSystemResourceExportPage1 {

	private String exportFilePath;

	public String getExportFilePath() {
		return exportFilePath;
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean finish() {
		List<org.eclipse.core.internal.resources.File> resourcesToExport = (List<org.eclipse.core.internal.resources.File>) getWhiteCheckedResources();

		return executeExportOperation(resourcesToExport, getDestinationValues(resourcesToExport));
	}

	protected boolean executeExportOperation(List<org.eclipse.core.internal.resources.File> resourcesToExport,
			List<String> destinationValues) {
		// Call here the code to perform the SBOL export

		return false;
	}

	/**
	 * Answer the contents of self's destination specification widget. If this value
	 * does not have a suffix then add it first.
	 */
	protected List<String> getDestinationValues(List<org.eclipse.core.internal.resources.File> resourcesToExport) {
		String idealSuffix = getOutputSuffix();

		List<String> destinationValues = new ArrayList<String>();

		// only append a suffix if the destination doesn't already have a . in
		// its last path segment.
		// Also prevent the user from selecting a directory. Allowing this will
		// create a ".sbol" file in the directory
		for (org.eclipse.core.internal.resources.File resourceToExport : resourcesToExport) {
			String filePath = super.getDestinationValue();
			String fileNameWithoutExtension = resourceToExport.getName().substring(0,
					resourceToExport.getName().lastIndexOf('.'));

			if (filePath.length() != 0 && !filePath.endsWith(File.separator)) {
				int dotIndex = filePath.lastIndexOf('.');
				if (dotIndex != -1) {
					// the last path seperator index
					int pathSepIndex = filePath.lastIndexOf(File.separator);
					if (pathSepIndex != -1 && dotIndex < pathSepIndex) {
						filePath += File.separator + fileNameWithoutExtension + idealSuffix;
					}
				} else {
					filePath += File.separator + fileNameWithoutExtension + idealSuffix;
				}
			}

			destinationValues.add(filePath);
		}

		return destinationValues;
	}

	/**
	 * Answer the suffix that files exported from this wizard should have. If this
	 * suffix is a file extension (which is typically the case) then it must include
	 * the leading period character.
	 *
	 */
	protected String getOutputSuffix() {
		return ".sbol";
	}

	@SuppressWarnings("restriction")
	public SBOLExportWizardPage(IStructuredSelection selection) {
		super(selection);
		setTitle("SBOL Export Settings");
		setDescription("Please configure the details of the SBOL export");
	}
}
