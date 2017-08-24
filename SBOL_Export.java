package roadblock.dataprocessing.export;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sbolstandard.core2.AccessType;
import org.sbolstandard.core2.Component;
import org.sbolstandard.core2.ComponentDefinition;
import org.sbolstandard.core2.DirectionType;
import org.sbolstandard.core2.FunctionalComponent;
import org.sbolstandard.core2.Interaction;
import org.sbolstandard.core2.Module;
import org.sbolstandard.core2.ModuleDefinition;
import org.sbolstandard.core2.OrientationType;
import org.sbolstandard.core2.RefinementType;
import org.sbolstandard.core2.RestrictionType;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.Sequence;
import org.sbolstandard.core2.SequenceAnnotation;
import org.sbolstandard.core2.SequenceOntology;
import org.sbolstandard.core2.TopLevel;
import roadblock.emf.bioparts.Bioparts.BiocompilerCell;
import roadblock.emf.bioparts.Bioparts.BiocompilerDevice;
import roadblock.emf.bioparts.Bioparts.BiocompilerModel;
import roadblock.emf.bioparts.Bioparts.Biopart;
import roadblock.emf.ibl.Ibl.Cell;
import roadblock.emf.ibl.Ibl.Device;
import roadblock.emf.ibl.Ibl.Model;
import roadblock.emf.ibl.Ibl.MolecularSpecies;
import roadblock.emf.ibl.Ibl.Region;
import roadblock.emf.ibl.Ibl.Rule;

public class SBOL_Export {

	// Helper marker for uniqifying display IDs via incrementation
	private static int ID = 1;
	// Version of components being built
	private static String version = "1";

	/**
	 * This helper function turns a string into an SBOL-compatible ID.
	 * 
	 * @param displayId
	 *            the ID to be made SBOL-compatible if in an improper format or null
	 * @return the fixed displayId
	 */
	private static String fixDisplayID(String displayId) {
		if (displayId == null || displayId.equals(""))
			return "Unnamed" + (ID++);
		int index = Math.max(displayId.lastIndexOf('/'),
				Math.max(displayId.lastIndexOf('#'), displayId.lastIndexOf(':')));
		if (index != -1)
			displayId = displayId.substring(index + 1);
		displayId = displayId.replaceAll("[^a-zA-Z0-9_]", "_");
		displayId = displayId.replace(" ", "_");
		if (Character.isDigit(displayId.charAt(0))) {
			displayId = "_" + displayId;
		}
		return displayId;
	}

	private static boolean isDNA(String type) {
		return type.equals("PROMOTER") || type.equals("GENE") 
				|| type.equals("RBS") || type.equals("TERMINATOR");
	}
	
	/**
	 * This function creates or finds a component definition for a MolecularSpecies
	 * given certain parameters, which include the MolecularSpecies' bioType that is
	 * translated to a SBO type.
	 * 
	 * @param doc
	 *            the overall SBOL model to fetch/create the component definition
	 *            from
	 * @param displayID
	 *            the ID to give to the component definition
	 * @param bioType
	 *            the type of the component definition to be created
	 * @param wasDerived
	 *            an optional string specifying the originating URI of the
	 *            component definition
	 * @param reportMissing
	 *            a boolean to note if no existing component definition is found
	 *            for wasDerived
	 * @return the fetched or newly created component definition
	 * @throws Exception
	 */
	private static ComponentDefinition createCompDef(SBOLDocument doc, String displayID, String bioType, URI componentType,
			String wasDerived, Boolean reportMissing) throws Exception {
		URI role = null;
		
		URI type = componentType;
		if (type == null) {
			if (bioType != null) {
				switch (bioType) {
					case "MOLECULE": { type = ComponentDefinition.SMALL_MOLECULE; break; }
					case "COMPLEX": { type = ComponentDefinition.COMPLEX; break; }
					case "RIBOSOME": { type = ComponentDefinition.COMPLEX; break; }
					case "PROTEIN": { type = ComponentDefinition.PROTEIN; break; }
					case "DNA": { type = ComponentDefinition.DNA; break; }
					case "RNA": { type = ComponentDefinition.RNA; break; }
					case "GENE": { type = ComponentDefinition.DNA; role = SequenceOntology.GENE; break; }
					case "INTEGER": { throw new Exception("Integer type not allowed in molecule " + displayID); }
					case "RATE": { throw new Exception("Rate type not allowed in molecule " + displayID); }
					case "RBS": { type = ComponentDefinition.DNA; role = SequenceOntology.RIBOSOME_ENTRY_SITE; break; }
					case "PROMOTER": { type = ComponentDefinition.DNA; role = SequenceOntology.PROMOTER; break; }
					case "TERMINATOR": { type = ComponentDefinition.DNA; role = SequenceOntology.TERMINATOR; break; }
					case "CLONINGSITE": { type = ComponentDefinition.DNA; role = URI.create("http://identifiers.org/so/SO:0001687"); break; }
					default: { type = ComponentDefinition.SMALL_MOLECULE; break; }
				}
			} else {
				type = ComponentDefinition.SMALL_MOLECULE;
			}
		}

		URI derivedURI = null;
		// Check if derivedURI, if present, is unique
		if (wasDerived != null && wasDerived != "") {
			try {
				derivedURI = new URI(wasDerived);
				Set<TopLevel> fetch = doc.getByWasDerivedFrom(derivedURI);
				for (TopLevel tl : fetch) {
					if (tl instanceof ComponentDefinition) {
						return (ComponentDefinition) tl;
					}
				}
			} catch (URISyntaxException e) {
				System.out.println("Incorrect URI format for molecule " + displayID);
				e.printStackTrace();
			}
		}

		if (reportMissing) {
			System.out.println("Component " + displayID + " was unable to be found.");
		}

		// Get unique ID
		int uniqify = 1;
		String newDisplayID = displayID;
		while (doc.getComponentDefinition(newDisplayID, version) != null) {
			newDisplayID = displayID + uniqify;
			uniqify++;
		}
		displayID = newDisplayID;

		ComponentDefinition def = doc.createComponentDefinition(displayID, version, type);
		if (derivedURI != null)
			def.addWasDerivedFrom(derivedURI);
		if (role != null)
			def.addRole(role);
		return def;
	}

	/**
	 * This function creates a subcomponent definition and adds a subcomponent and sequence
	 * annotation to a component definition it takes in with specified parameters.
	 * 
	 * @param compDef
	 * @param document
	 * @param part
	 * @param sequenceStart
	 * @param sequenceEnd
	 * @param version
	 * @throws Exception
	 */
	private static void addPartComponent(ComponentDefinition compDef, SBOLDocument document, Biopart part, int direction,
			int sequenceStart, int sequenceEnd, String wasDerived) throws Exception {

		String partName = fixDisplayID(part.getName());
		ComponentDefinition subCompDef = createCompDef(document, partName, null, ComponentDefinition.DNA, wasDerived, true);
		partName = subCompDef.getDisplayId();

		OrientationType orientation = (direction == 1 ? OrientationType.INLINE : OrientationType.REVERSECOMPLEMENT);
		SequenceAnnotation curAnnotation = compDef.createSequenceAnnotation(partName + "_annotation", "range",
				sequenceStart, sequenceEnd, orientation);

		URI partType;
		// Use the predefined SequenceOntology constant
		switch (part.getBiologicalFunction()) {
			case "PROMOTER": { partType = SequenceOntology.PROMOTER; break; }
			case "GENE": { partType = SequenceOntology.CDS; break; }
			case "RBS": { partType = SequenceOntology.RIBOSOME_ENTRY_SITE; break; }
			case "TERMINATOR": { partType = SequenceOntology.TERMINATOR; break; }
			default: { partType = URI.create("http://identifiers.org/so/SO:0000110"); break; }
		}
		subCompDef.addRole(partType);
		Sequence partSequence = document.createSequence(partName + "_sequence", part.getSequence().toLowerCase(), Sequence.IUPAC_DNA);
		subCompDef.addSequence(partSequence);

		compDef.createComponent(partName, AccessType.PRIVATE, partName);
		curAnnotation.setComponent(partName);

	}

	/**
	 * This function creates ComponentDefinitions from and instantiates a group of
	 * MolecularSpecies in a ModuleDefinition.
	 * 
	 * @param moleculeList
	 *            a List of MolecularSpecies to be converted.
	 * @param parent
	 *            the ModuleDefinition where the FunctionalComponents will be
	 *            instantiated.
	 * @param document
	 *            the SBOL model where the ComponentDefinitions will be created.
	 * @throws Exception
	 */
	private static void convertMolecules(List<MolecularSpecies> moleculeList, ModuleDefinition parent,
			SBOLDocument document) throws Exception {
		for (MolecularSpecies ms : moleculeList) {
			String molecularID = fixDisplayID(ms.getDisplayName());
			if (document.getComponentDefinition(molecularID, version) == null) {
				createCompDef(document, molecularID, ms.getBiologicalType(), null, ms.getURI(), false);
			}
			parent.createFunctionalComponent(molecularID, AccessType.PRIVATE, molecularID, version, DirectionType.NONE);
		}
	}

	/**
	 * This function parses the input and output lists of a group of devices,
	 * mapping the molecules to the parent cell molecules and creating Interactions
	 * between the input/output molecules and the DNA component of the device. The
	 * subcomponents of the device are gathered from the biocompiled model and
	 * aggregated into one component definition.
	 * 
	 * @param devices
	 *            a List of Devices.
	 * @param cMoleculeList
	 *            a List of MolecularSpecies in the Devices' parent cell.
	 * @param parent
	 *            the ModuleDefinition corresponding to the parent cell.
	 * @param document
	 *            the overall SBOL model.
	 * @param bCell
	 *            the BiocompilerCell corresponding to the Cell from which the
	 *            List of Devices was obtained.
	 * @throws Exception
	 */
	private static void convertDevices(List<Device> devices, List<MolecularSpecies> cMoleculeList,
			ModuleDefinition parent, SBOLDocument document, BiocompilerCell bCell) throws Exception {
		for (Device d : devices) {
			String deviceName = fixDisplayID(d.getDisplayName());
			ModuleDefinition device = document.createModuleDefinition(deviceName, version);
			Module module = parent.createModule(deviceName + "_module", deviceName, version);

			String partListID = deviceName + "_parts";
			ComponentDefinition partList = createCompDef(document, partListID, null, ComponentDefinition.DNA, null,
					false);
			partList.addRole(SequenceOntology.ENGINEERED_REGION);
			device.createFunctionalComponent(partListID, AccessType.PRIVATE, partListID, DirectionType.NONE);

			if (bCell != null) {
				// Add DNA parts in a biocompiled model
				for (BiocompilerDevice bDevice : bCell.getDevices()) {
					if (bDevice.getName().equals(d.getDisplayName())) {
						int direction = bDevice.getDirection().value();
						ArrayList<Biopart> allParts = new ArrayList<Biopart>(bDevice.getParts());
						Collections.sort(allParts, new Comparator<Biopart>() {
							public int compare(Biopart a, Biopart b) {
								return a.getPosition().value() > b.getPosition().value() ? 1 : -1;
							}
						});

						int sequenceStart = 1;
						// Create a subcomponent corresponding to each part in the current cell
						for (Biopart part : allParts) {
							String wasDerived = "";
							for (MolecularSpecies mPart : cMoleculeList) {
								if (mPart.getDisplayName().equals(part.getName())) {
									wasDerived = mPart.getURI();
									break;
								}
							}
							int seqLength = part.getSequence().length();
							addPartComponent(partList, document, part, direction, sequenceStart,
									sequenceStart + seqLength - 1, wasDerived);
							// Update the start index to reflect the next part we're iterating through
							sequenceStart = sequenceStart + seqLength;
						}

						// Store the overall nucleotide sequence in the overall component definition of the cell
						String stringSeq = partList.getImpliedNucleicAcidSequence();
						Sequence wholeSequence = document.createSequence(deviceName + "_sequence", version, stringSeq,
								Sequence.IUPAC_DNA);
						partList.addSequence(wholeSequence);
						break;
					}
				}
			}
			else {
				// Add DNA parts in a non-biocompiled model
				String previous = null, current = null;
				for (MolecularSpecies ms : d.getPartList()) {
					current = fixDisplayID(ms.getDisplayName());
					partList.createComponent(current + "_subcomponent", AccessType.PRIVATE, current);
					if (previous != null) {
						partList.createSequenceConstraint(previous + "_" + current + "_constraint",
								RestrictionType.PRECEDES, previous + "_subcomponent", current + "_subcomponent");
					}
					previous = current;
				}
			}
			
			// Map device inputs and outputs to cell molecules via MapTos
			for (MolecularSpecies ms : d.getInputList()) {
				String molecularID = fixDisplayID(ms.getDisplayName());
				String inputName = molecularID + "_input";
				FunctionalComponent f = device.createFunctionalComponent(inputName, AccessType.PUBLIC, molecularID,
						version, DirectionType.IN);
				module.createMapsTo(molecularID, RefinementType.VERIFYIDENTICAL, molecularID, f.getDisplayId());
				Interaction in = device.createInteraction(molecularID + "_interaction",
						URI.create("http://identifiers.org/biomodels.sbo/SBO:0000168"));
				in.createParticipation(molecularID + "_modifier", inputName,
						URI.create("http://identifiers.org/biomodels.sbo/SBO:0000019"));
				in.createParticipation(partListID + "_modified", partListID,
						URI.create("http://identifiers.org/biomodels.sbo/SBO:0000644"));
			}
			for (MolecularSpecies ms : d.getOutputList()) {
				String molecularID = fixDisplayID(ms.getDisplayName());
				String outputName = molecularID + "_output";
				FunctionalComponent f = device.createFunctionalComponent(outputName, AccessType.PUBLIC, molecularID,
						version, DirectionType.OUT);
				module.createMapsTo(molecularID, RefinementType.VERIFYIDENTICAL, molecularID, f.getDisplayId());
				Interaction out = device.createInteraction(outputName + "_interaction",
						URI.create("http://identifiers.org/biomodels.sbo/SBO:0000589"));
				out.createParticipation(molecularID + "_product", outputName,
						URI.create("http://identifiers.org/biomodels.sbo/SBO:0000011"));
				out.createParticipation(partListID + "_template", partListID,
						URI.create("http://identifiers.org/biomodels.sbo/SBO:0000645"));
			}
		}
	}

	/**
	 * This function parses and instantiates each MoleculeList, RuleList, and
	 * DeviceList of a group of Cells.
	 * 
	 * @param cells
	 *            a List of Cells.
	 * @param parent
	 *            the ModuleDefinition corresponding to the either the Region or
	 *            Model where the List of Cells was obtained.
	 * @param document
	 *            the SBOL model where the ComponentDefinitions will be created.
	 * @param biocompilerModel
	 *            the parent of BiocompilerCells, which are related to Cells.
	 * @throws Exception
	 */
	private static void convertCells(List<Cell> cells, ModuleDefinition parent, SBOLDocument document,
			BiocompilerModel biocompilerModel) throws Exception {
		for (Cell c : cells) {
			String cellName = fixDisplayID(c.getDisplayName());
			ModuleDefinition cell = document.createModuleDefinition(cellName, version);
			parent.createModule(cellName + "_module", cellName);

			BiocompilerCell bCell = null;
			if (biocompilerModel != null) {
				for (BiocompilerCell iterateCell : biocompilerModel.getCells()) {
					if (iterateCell.getName().equals(c.getDisplayName())) {
						bCell = iterateCell;
					}
				}
			}

			HashSet<String> cellMolecules = new HashSet<String>();
			// Create all component definitions for cell molecules
			for (MolecularSpecies ms : c.getMoleculeList()) {
				String molecularID = fixDisplayID(ms.getDisplayName());
				if (cellMolecules.contains(molecularID))
					continue;
				cellMolecules.add(molecularID);
				String type = ms.getBiologicalType();
				createCompDef(document, molecularID, type, null, ms.getURI(), false);
				if (type == null || !isDNA(type)) {
					cell.createFunctionalComponent(molecularID, AccessType.PUBLIC, molecularID, version,
							DirectionType.NONE);
				}
			}

			convertDevices(c.getDeviceList(), c.getMoleculeList(), cell, document, bCell);

			HashSet<String> inPartLists = new HashSet<String>();
			// Instantiate DNA components that haven't been instantiated in a device
			for (MolecularSpecies m1 : c.getMoleculeList()) {
				Boolean instantiated = false;
				if (isDNA(m1.getBiologicalType())) {
					for (Device d : c.getDeviceList()) {
						for (MolecularSpecies m2 : d.getPartList()) {
							if(m1.getDisplayName().equals(m2.getDisplayName())) {
								instantiated = true;
							}
						}
					}
					String molecularID = fixDisplayID(m1.getDisplayName());
					if (!instantiated) {
						cell.createFunctionalComponent(molecularID, AccessType.PRIVATE, molecularID,
								DirectionType.NONE);
					} 
					else {
						inPartLists.add(molecularID);
					}
				}
			}

			// Map rules to interactions. A DNA component is not allowed to appear in both
			// a rule and a device partList.
			for (Rule r : c.getRuleList()) {
				Interaction interaction = cell.createInteraction(fixDisplayID(r.getDisplayName()),
						URI.create("http://identifiers.org/biomodels.sbo/SBO:0000176"));
				HashSet<String> reactants = new HashSet<String>();
				for (MolecularSpecies reactant : r.getLeftHandSide()) {
					if (reactants.contains(reactant.getDisplayName()))
						continue;
					reactants.add(reactant.getDisplayName());
					String molecularID = fixDisplayID(reactant.getDisplayName());
					interaction.createParticipation(molecularID + "_reactant", molecularID,
							URI.create("http://identifiers.org/biomodels.sbo/SBO:0000010"));
					String type = reactant.getBiologicalType();
					if (type != null && isDNA(type) && inPartLists.contains(molecularID)) {
							throw new Exception(
									"The DNA part " + molecularID + " appears in both a cell rule and a device part.");
					}
				}
				HashSet<String> products = new HashSet<String>();
				for (MolecularSpecies product : r.getRightHandSide()) {
					if (products.contains(product.getDisplayName()))
						continue;
					products.add(product.getDisplayName());
					String molecularID = fixDisplayID(product.getDisplayName());
					interaction.createParticipation(molecularID + "_product", molecularID,
							URI.create("http://identifiers.org/biomodels.sbo/SBO:0000011"));
					String type = product.getBiologicalType();
					if (type != null && isDNA(type) && inPartLists.contains(molecularID)) {
							throw new Exception(
									"The DNA part " + molecularID + " appears in both a cell rule and a device part.");
					}
				}
			}
		}
	}

	/**
	 * This function parses and instantiates the MoleculeList and/or the CellList of
	 * a group of Region objects.
	 * 
	 * @param regions
	 *            a List of Regions.
	 * @param parent
	 *            the ModuleDefinition corresponding to the Model where the List
	 *            of Regions was obtained.
	 * @param document
	 *            the SBOL model where the ComponentDefinitions of objects within
	 *            a Region will be created.
	 * @param biocompilerModel
	 *            the parent of BiocompilerCells, which are related to Cells.
	 * @throws Exception
	 */
	private static void convertRegions(List<Region> regions, ModuleDefinition parent, SBOLDocument document,
			BiocompilerModel biocompilerModel) throws Exception {

		for (Region r : regions) {
			String regionID = fixDisplayID(r.getDisplayName());
			ModuleDefinition region = document.createModuleDefinition(regionID, version);
			List<MolecularSpecies> moleculeList = r.getMoleculeList();
			if (moleculeList != null && !moleculeList.isEmpty()) {
				convertMolecules(moleculeList, region, document);
			} else {
				convertCells(r.getCellList(), region, document, biocompilerModel);
				parent.createModule(regionID + "_module", regionID);
			}
		}
	}

	/**
	 * This function reads the information from an EMF model and an optional
	 * BiocompilerModel into a SBOL document.
	 * 
	 * @param model
	 *            a structure storing hierarchical and functional information
	 * @param biocompilerModel
	 *            a structure storing biocompilation information
	 * @param namespace
	 *            the namespace used for the SBOL document.
	 * @return is the created SBOL document.
	 * @throws Exception
	 */
	public static SBOLDocument makeSBOLDocument(Model model, BiocompilerModel biocompilerModel, String namespace)
			throws Exception {

		SBOLDocument document = new SBOLDocument();
		document.setDefaultURIprefix(namespace);

		document.setComplete(true); // Throw exceptions when URIs are incorrect
		document.setCreateDefaults(true); // Default components and/or functional component instances are created when
		// not present
		document.setTypesInURIs(false); // Types aren't inserted into top-level identity URIs when they are created

		ModuleDefinition modelDef = document.createModuleDefinition(fixDisplayID(model.getDisplayName()));

		List<Region> regions = model.getRegionList();
		List<Cell> cells = model.getCellList();
		List<Device> devices = model.getDeviceList();
		List<MolecularSpecies> molecules = model.getMoleculeList();

		if (regions != null && !regions.isEmpty()) {
			convertRegions(regions, modelDef, document, biocompilerModel);
		} 
		else if (cells != null && !cells.isEmpty()) {
			convertCells(cells, modelDef, document, biocompilerModel);
		} 
		else if (devices != null && !devices.isEmpty()) {
			convertDevices(devices, null, modelDef, document, null);
		} 
		else {
			convertMolecules(molecules, modelDef, document);
		}

		ID = 1;
		return document;
	}

}
