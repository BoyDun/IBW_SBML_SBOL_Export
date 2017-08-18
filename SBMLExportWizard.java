package roadblock.ibw.exports.sbml;

import java.util.List;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;
import org.eclipse.ui.internal.wizards.datatransfer.DataTransferMessages;
import org.eclipse.ui.wizards.datatransfer.ZipFileExportWizard;

@SuppressWarnings("restriction")
public class SBMLExportWizard extends Wizard implements IExportWizard {

	private IStructuredSelection selection;
	private SBMLExportWizardPage wizardPage;

	@Override
	public String getWindowTitle() {
		return "SBML Export";
	}

	@Override
	public void addPages() {
		wizardPage = new SBMLExportWizardPage(selection);
		addPage(wizardPage);
		new ZipFileExportWizard();
	}

	@Override
	public boolean performFinish() {
		return wizardPage.finish();
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection currentSelection) {
		this.selection = currentSelection;

		List<?> selectedResources = IDE.computeSelectedResources(currentSelection);
		if (!selectedResources.isEmpty()) {
			this.selection = new StructuredSelection(selectedResources);
		}

		setWindowTitle(DataTransferMessages.DataTransfer_export);
		setNeedsProgressMonitor(true);
	}
}