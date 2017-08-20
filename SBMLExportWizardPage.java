package roadblock.ibw.exports.sbml;

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
public class SBMLExportWizardPage extends WizardFileSystemResourceExportPage1 {

	private Composite container;
	private Text name;
	private Text directoryPath;
	private boolean toFlatten = false;

	/**
	 * Overriden function that specifies the components visible to the user on the
	 * page. For SBML export, the components include text fields to input a file
	 * name and a directory path. It also includes a button to browse for a
	 * directory and a checkbox to choose whether or not to flatten the EMF model.
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
				if (!name.getText().isEmpty() && !directoryPath.getText().isEmpty()) {
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
				if (!name.getText().isEmpty() && !directoryPath.getText().isEmpty()) {
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
				if (!directoryPath.getText().isEmpty()) {
					setPageComplete(true);
				}
			}
		});

		Button checkBox = new Button(container, SWT.CHECK);
		checkBox.setText("Export flattened model");
		checkBox.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				Button btn = (Button) event.getSource();
				toFlatten = btn.getSelection();
			}
		});

		// required to avoid an error in the system
		setControl(container);
		setPageComplete(false);

	}

	/**
	 * Method that is called when the finish button is clicked.
	 */
	@Override
	public boolean finish() {
		return SBMLExportInterface.convertSBML(directoryPath.getText(), name.getText(), toFlatten);
	}

	/**
	 * Miscellaneous page settings.
	 * 
	 * @param selection
	 */
	public SBMLExportWizardPage(IStructuredSelection selection) {
		super(selection);
		setTitle("SBML Export Settings");
		setDescription("Please configure details of the SBML export");
	}
}
