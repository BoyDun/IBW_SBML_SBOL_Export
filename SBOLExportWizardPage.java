package roadblock.ibw.exports.sbol;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.internal.wizards.datatransfer.WizardFileSystemResourceExportPage1;

@SuppressWarnings("restriction")
public class SBOLExportWizardPage extends WizardFileSystemResourceExportPage1 {

	private Composite container;
	private Text name;
	private Text directoryPath;
	private Text namespace;
	private boolean toBiocompile = false;

	/**
	 * Helper function that checks if all necessary fields of the page are filled
	 * 
	 * @return
	 */
	private boolean shouldSetComplete() {
		return !name.getText().isEmpty() && !directoryPath.getText().isEmpty() && !namespace.getText().isEmpty();
	}

	/**
	 * Overriden function that specifies the components visible to the user on the
	 * page. For SBOL export, the components include text fields to input a file
	 * name, a directory path, and a SBOL namespace. It also includes a button to
	 * browse for a directory and a checkbox to choose whether or not to biocompile
	 * the EMF model.
	 */
	@Override
	public void createControl(Composite parent) {
		container = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		container.setLayout(layout);

		Label label1 = new Label(container, SWT.NONE);
		label1.setText("Name: ");
		name = new Text(container, SWT.BORDER | SWT.SINGLE);
		name.setText("");
		name.addKeyListener(new KeyListener() {

			@Override
			public void keyPressed(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (shouldSetComplete()) {
					setPageComplete(true);
				}
			}

		});
		GridData gd1 = new GridData(GridData.FILL_HORIZONTAL);
		name.setLayoutData(gd1);

		Label label2 = new Label(container, SWT.NONE);
		label2.setText("To directory: ");
		directoryPath = new Text(container, SWT.BORDER | SWT.SINGLE);
		directoryPath.setText("");
		directoryPath.addKeyListener(new KeyListener() {

			@Override
			public void keyPressed(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (shouldSetComplete()) {
					setPageComplete(true);
				}
			}

		});
		GridData gd2 = new GridData(GridData.FILL_HORIZONTAL);
		directoryPath.setLayoutData(gd2);

		Button b = new Button(container, SWT.PUSH | SWT.BORDER);
		b.setText("Browse");
		b.addMouseListener(new MouseListener() {
			@Override
			public void mouseDoubleClick(MouseEvent e) {

			}

			@Override
			public void mouseUp(MouseEvent e) {
			}

			@Override
			public void mouseDown(MouseEvent e) {
				DirectoryDialog dialog = new DirectoryDialog(new Shell(), SWT.OPEN);
				dialog.setFilterPath("c:\\temp");
				directoryPath.setText(dialog.open());
				if (shouldSetComplete()) {
					setPageComplete(true);
				}
			}
		});

		Label label3 = new Label(container, SWT.NONE);
		label3.setText("SBOL Namespace: ");
		namespace = new Text(container, SWT.BORDER | SWT.SINGLE);
		namespace.setText("");
		namespace.addKeyListener(new KeyListener() {

			@Override
			public void keyPressed(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (!directoryPath.getText().isEmpty() && !namespace.getText().isEmpty()) {
					setPageComplete(true);
				}
			}

		});
		GridData gd3 = new GridData(GridData.FILL_HORIZONTAL);
		namespace.setLayoutData(gd3);

		Button checkBox = new Button(container, SWT.CHECK);
		checkBox.setText("Export biocompiled model");
		checkBox.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				Button btn = (Button) event.getSource();
				toBiocompile = btn.getSelection();
			}
		});

		// required to avoid an error in the system
		setControl(container);
		setPageComplete(false);

	}

	/**
	 * Method that is called when the finish button is clicked. If the namespace is
	 * not valid, an error dialog appears and the user stays at the export page.
	 */
	@Override
	public boolean finish() {
		if (!SBOLExportInterface.verifyNamespace(namespace.getText())) {
			MessageDialog.openError(null, "Invalid SBOL Namespace", "The namespace you entered cannot "
					+ "represent a valid SBOL document. Please enter a valid namespace.");
			return false;
		}
		return SBOLExportInterface.convertSBOL(directoryPath.getText(), name.getText(), namespace.getText(),
				toBiocompile);
	}

	/**
	 * Miscellaneous page settings.
	 * 
	 * @param selection
	 */
	public SBOLExportWizardPage(IStructuredSelection selection) {
		super(selection);
		setTitle("SBOL Export Settings");
		setDescription("Please configure details of the SBOL export");
	}
}
