package roadblock.dataprocessing.export;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.Preferences;

import org.sbml.jsbml.ASTNode;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.KineticLaw;
import org.sbml.jsbml.LocalParameter;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBase;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.SpeciesReference;
import org.sbml.jsbml.Unit;
import org.sbml.jsbml.Unit.Kind;
import org.sbml.jsbml.UnitDefinition;
import org.sbml.jsbml.ext.comp.CompConstants;
import org.sbml.jsbml.ext.comp.CompModelPlugin;
import org.sbml.jsbml.ext.comp.CompSBMLDocumentPlugin;
import org.sbml.jsbml.ext.comp.CompSBasePlugin;
import org.sbml.jsbml.ext.comp.Port;
import org.sbml.jsbml.ext.comp.ReplacedElement;
import org.sbml.jsbml.ext.comp.Submodel;
import org.sbml.jsbml.text.parser.FormulaParserLL3;
import org.sbml.jsbml.text.parser.IFormulaParser;
import org.sbml.jsbml.text.parser.ParseException;

import roadblock.emf.ibl.Ibl.Cell;
import roadblock.emf.ibl.Ibl.Device;
import roadblock.emf.ibl.Ibl.FlatModel;
import roadblock.emf.ibl.Ibl.MolecularSpecies;
import roadblock.emf.ibl.Ibl.RateUnit;
import roadblock.emf.ibl.Ibl.Region;
import roadblock.emf.ibl.Ibl.Rule;

public class SBML_Export {
    
	//Helper marker for uniqifying display IDs via incrementation
	private static int ID = 1;
	//SBML level
	private static int level = 3;
	//Version of components being created
	private static int version = 1;
	
	/**
	 * This helper function turns a string into an SBOL-compatible ID.
	 * 
	 * @param displayId is the ID to be made SBOL-compatible if in an improper format / null
	 * @return the fixed displayId
	 */
	private static String fixDisplayID(String displayId) {
		if(displayId == null || displayId.equals("")) return "Unnamed" + (ID++);
		int index = Math.max(displayId.lastIndexOf('/'), Math.max(displayId.lastIndexOf('#'), displayId.lastIndexOf(':')));
		if (index != -1) displayId = displayId.substring(index + 1);
		displayId = displayId.replaceAll("[^a-zA-Z0-9_]", "_");
		displayId = displayId.replace(" ", "_");
		if (Character.isDigit(displayId.charAt(0))) { 
			displayId = "_" + displayId;
		}
		return displayId;
	}
	
	/**
	 * 
	 * @param doc is the SBML document from which the plugin is created.
	 * @return the created plugin.
	 */
    private static CompSBMLDocumentPlugin getCompDocPlugin(SBMLDocument doc) {
		CompSBMLDocumentPlugin compDoc = new CompSBMLDocumentPlugin(doc);
		doc.addExtension(CompConstants.namespaceURI, compDoc);
		return compDoc;
    }
    
    /**
     * 
     * @param model is the model from which the plugin is created.
     * @return the created plugin.
     */
    private static CompModelPlugin getCompModelPlugin(Model model) {
    	CompModelPlugin compModel = new CompModelPlugin(model);
    	model.addExtension(CompConstants.namespaceURI, compModel);
    	return compModel;
    }
    
    /**
     * 
     * @param sb is the SBML base object from which the plugin is created.
     * @return the created plugin.
     */
    private static CompSBasePlugin getCompSBasePlugin(SBase sb) {
    	CompSBasePlugin compBase = new CompSBasePlugin(sb);
    	sb.addExtension(CompConstants.namespaceURI, compBase);
    	return compBase;
    }
    
    /**
     * Convert a String into an ASTNode.
     * 
     * @param formula is the string to convert.
     * @return the converted node.
     */
    private static ASTNode parseFormula(String formula) {
    	ASTNode mathFormula = null;
    	Preferences.userRoot();
    	IFormulaParser parser = new FormulaParserLL3(new StringReader(""));
    	try {
			mathFormula = ASTNode.parseFormula(formula, parser);
		} catch (ParseException e) {
			return null;
		} catch (Exception e) {
			return null;
		}
    	if (mathFormula == null) {
    		return null;
    	}
    	return mathFormula;
    }
    
    /**
     * Takes in a model and defines all possible IBW units in it.
     * 
     * @param m is the model where the units are defined.
     */
    private static void createUnits(Model m) {
    	UnitDefinition ud1 = m.createUnitDefinition("M");
    	ud1.createUnit(Kind.MOLE);
    	Unit u1b = ud1.createUnit(Kind.LITRE);
    	u1b.setExponent(-1.0);
    	UnitDefinition ud2 = m.createUnitDefinition("mM");
    	Unit u2a = ud2.createUnit(Kind.MOLE);
    	u2a.setScale(-3);
    	Unit u2b = ud2.createUnit(Kind.LITRE);
    	u2b.setExponent(-1.0);
    	UnitDefinition ud3 = m.createUnitDefinition("uM");
    	Unit u3a = ud3.createUnit(Kind.MOLE);
    	u3a.setScale(-6);
    	Unit u3b = ud3.createUnit(Kind.LITRE);
    	u3b.setExponent(-1.0);
    	UnitDefinition ud4 = m.createUnitDefinition("nM");
    	Unit u4a = ud4.createUnit(Kind.MOLE);
    	u4a.setScale(-9);
    	Unit u4b = ud4.createUnit(Kind.LITRE);
    	u4b.setExponent(-1.0);
    	UnitDefinition ud5 = m.createUnitDefinition("pM");
    	Unit u5a = ud5.createUnit(Kind.MOLE);
    	u5a.setScale(-12);
    	Unit u5b = ud5.createUnit(Kind.LITRE);
    	u5b.setExponent(-1.0);
    	UnitDefinition ud6 = m.createUnitDefinition("fM");
    	Unit u6a = ud6.createUnit(Kind.MOLE);
    	u6a.setScale(-15);
    	Unit u6b = ud6.createUnit(Kind.LITRE);
    	u6b.setExponent(-1.0);
    	UnitDefinition ud7 = m.createUnitDefinition("MOLECULE");
    	ud7.createUnit(Kind.ITEM);
    	UnitDefinition ud8 = m.createUnitDefinition("fL");
    	Unit u8a = ud8.createUnit(Kind.LITRE);
    	u8a.setScale(-15);
    }
    
    /**
     * This function takes in a MolecularSpecies and creates an equivalent SBML Species in the 
     * specified compartment and model.
     * 
     * @param ms is the MolecularSpecies to convert.
     * @param displayID is the ID to set the newly created Species to.
     * @param model is the model in which the Species is being created.
     * @param compartment is the compartment in which the Species is being created.
     * @return the created Species.
     */
    private static Species createSpecies(MolecularSpecies ms, String displayID, Model model, Compartment compartment) {
		Species species = model.createSpecies(displayID, compartment);
		species.setHasOnlySubstanceUnits(false);
		species.setBoundaryCondition(false);
		species.setConstant(false);
		if (ms.getAmount() != 0) species.setInitialAmount(ms.getAmount());
		else species.setInitialAmount(0);
		if(ms.getUnit() != null) {
			String unit = ms.getUnit().getLiteral();
			species.setUnits(unit);
			if (unit == "MOLECULE") species.setHasOnlySubstanceUnits(true);
		}
		return species;
    }
    
    /**
     * Sets a Species in the parent model as the replacement for a Species object in its submodel.
     * 
     * @param ms is the MolecularSpecies corresponding to the Species that are participating in the replacement.
     * @param displayId is the ID to set the newly created submodel Species to.
     * @param submodelRef is the name of the submodel that the ReplacedElement is referring to.
     * @param model is the model containing the Species doing the replacing.
     * @param subModel is the model containing the species being replaced.
     * @param compartment is the subcompartment where the newly created Species will be located.
     * @param plugin is the plugin of the model in which the port will be created.
     */
    private static void setReplacement(MolecularSpecies ms, String displayId, String submodelRef,
    		Model model, Model subModel, Compartment compartment, CompModelPlugin plugin) {
		String mName = fixDisplayID(ms.getDisplayName());
		//Device species
		createSpecies(ms, displayId, subModel, compartment);
    	if (model.containsSpecies(mName + "_molecule")) {
    		//Cell species
			Species replacementSpecies = model.getSpecies(mName + "_molecule");
			CompSBasePlugin speciesPlugin = getCompSBasePlugin(replacementSpecies);

			Port speciesPort = plugin.createPort();
			speciesPort.setId(mName + "_species_port");
			speciesPort.setIdRef(displayId);
			
			ReplacedElement reSpecies = speciesPlugin.createReplacedElement();
			reSpecies.setSubmodelRef(submodelRef);
			reSpecies.setPortRef(mName + "_species_port");
		}
    }
    
    /**
     * Converts a Rule into a SBML Reaction with the proper reactants, products, units,
     * and kinetic law.
     * 
     * @param r is a Rule that will be converted into a SBML Reaction.
     * @param model is the Model in which the Reaction will be created.
     * @param compartment is the Compartment that the Reaction will be set to.
     */
	private static void setRule(Rule r, Model model, Compartment compartment) {
		int uniqify = 1;
		String displayID = fixDisplayID(r.getDisplayName());
		String newDisplayID = displayID;
		//Use unique ID
		while (model.getReaction(newDisplayID) != null) {
			newDisplayID = displayID + uniqify;
			uniqify++;
		}
		Reaction reaction = model.createReaction(newDisplayID);
		reaction.setCompartment(compartment);
		reaction.setSBOTerm("SBO:0000412"); //Biological activity
		reaction.setFast(false);
		
		KineticLaw k = reaction.createKineticLaw();
		LocalParameter forward = k.createLocalParameter("forward_rate");
		forward.setValue(r.getForwardRate());
		String equation = "forward_rate";
		HashMap<String, Integer> stoichReactants = new HashMap<String, Integer>();
		//Iterate over reactants
		for(MolecularSpecies ms : r.getLeftHandSide()) {
			String mName = fixDisplayID(ms.getDisplayName()) + "_molecule";
			if (!model.containsSpecies(mName)) createSpecies(ms, mName, model, compartment);
			SpeciesReference reactant = reaction.createReactant(model.getSpecies(mName));
			reactant.setConstant(false);
			if (stoichReactants.containsKey(mName)) stoichReactants.put(mName, stoichReactants.get(mName) + 1);
			else stoichReactants.put(mName, 1);
		}
		int totalFFreq = 0;
		for (String reactant : stoichReactants.keySet()) {
			int frequency = stoichReactants.get(reactant).intValue();
			totalFFreq += frequency;
			equation += ("*" + reactant + "^" + frequency);
		}
		totalFFreq--;
		
		// Creating the proper forward rate unit definition
		UnitDefinition fUnitDef = model.createUnitDefinition(newDisplayID + "_forward");
		Unit fTime = fUnitDef.createUnit(Kind.SECOND);
		fTime.setExponent(-1.0);
		if (r.getForwardRateUnit().getRateTimeUnit().toString().equals("PER_MINUTE")) {
			fTime.setMultiplier(60.0);
		}
		String unitString = r.getForwardRateUnit().getRateConcentrationUnit().toString();
		Unit fAmount;
		if (unitString.equals("PER_MOLECULE")) {
			fAmount = fUnitDef.createUnit(Kind.ITEM);
		}
		else {
			equation += ("*" + compartment.getId() + "^" + totalFFreq);
			Unit fVolume = fUnitDef.createUnit(Kind.LITRE);
			fVolume.setExponent((double)totalFFreq);
			fAmount = fUnitDef.createUnit(Kind.MOLE);
			if (unitString.equals("PER_M")) fAmount.setScale(0);
			else if (unitString.equals("PER_MM")) fAmount.setScale(-3);
			else if (unitString.equals("PER_UM")) fAmount.setScale(-6);
			else if (unitString.equals("PER_NM")) fAmount.setScale(-9);
			else if (unitString.equals("PER_PM")) fAmount.setScale(-12);
			else if (unitString.equals("PER_FM")) fAmount.setScale(-15);
		}
		fAmount.setExponent((double)-totalFFreq);
		forward.setUnits(newDisplayID + "_forward");
		
		HashMap<String, Integer> stoichProducts = new HashMap<String, Integer>();
		//Iterate over products
		for(MolecularSpecies ms : r.getRightHandSide()) {
			String mName = fixDisplayID(ms.getDisplayName()) + "_molecule";
			if (!model.containsSpecies(mName)) createSpecies(ms, mName, model, compartment);
			SpeciesReference product = reaction.createProduct(model.getSpecies(mName));
			product.setConstant(false);
			if (stoichProducts.containsKey(mName)) stoichProducts.put(mName, stoichProducts.get(mName) + 1);
			else stoichProducts.put(mName, 1);
		}
		if (r.isIsBidirectional()) {
			reaction.setReversible(true);
			LocalParameter reverse = k.createLocalParameter("reverse_rate");
			reverse.setValue(r.getReverseRate());
			equation += ("-" + "reverse_rate");
			int totalRFreq = 0;
			for (String product : stoichProducts.keySet()) {
				int frequency = stoichProducts.get(product).intValue();
				totalRFreq += frequency;
				equation += ("*" + product + "^" + frequency);
			}
			totalRFreq--;
			
			// Creating the proper reverse rate unit definition
			UnitDefinition rUnitDef = model.createUnitDefinition(newDisplayID + "_reverse");
			Unit rTime = rUnitDef.createUnit(Kind.SECOND);
			rTime.setExponent(-1.0);
			if (r.getReverseRateUnit().getRateTimeUnit().toString().equals("PER_MINUTE")) {
				rTime.setMultiplier(60.0);
			}
			String rUnitString = r.getReverseRateUnit().getRateConcentrationUnit().toString();
			Unit rAmount;
			if (rUnitString.equals("PER_MOLECULE")) {
				rAmount = rUnitDef.createUnit(Kind.ITEM);
			}
			else {
				equation += ("*" + compartment.getId() + "^" + totalRFreq);
				Unit rVolume = rUnitDef.createUnit(Kind.LITRE);
				rVolume.setExponent((double)totalRFreq);
				rAmount = rUnitDef.createUnit(Kind.MOLE);
				if (rUnitString.equals("PER_M")) rAmount.setScale(0);
				else if (rUnitString.equals("PER_MM")) rAmount.setScale(-3);
				else if (rUnitString.equals("PER_UM")) rAmount.setScale(-6);
				else if (rUnitString.equals("PER_NM")) rAmount.setScale(-9);
				else if (rUnitString.equals("PER_PM")) rAmount.setScale(-12);
				else if (rUnitString.equals("PER_FM")) rAmount.setScale(-15);
			}
			rAmount.setExponent((double)-totalRFreq);
			reverse.setUnits(newDisplayID + "_reverse");
		}
		else reaction.setReversible(false);

		//Set kinetic law
		ASTNode law = parseFormula(equation);
		k.setMath(law);
    }
    
	/**
	 * This function converts a group of MolecularSpecies into SBML Species in the proper Model and Compartment.
	 * 
	 * @param molecules is a List of MolecularSpecies to be converted.
	 * @param model is the Model in which the Species will be created.
	 * @param compartment is the Compartment that the Species will be set to.
	 */
    private static void convertMolecules(List<MolecularSpecies> molecules, Model model, Compartment compartment) {
		for (MolecularSpecies ms : molecules) {
			createSpecies(ms, fixDisplayID(ms.getDisplayName()) + "_molecule", model, compartment);
		}
    }
    
    /**
     * This function parses the input and output lists of a group of devices, mapping the Species to the proper 
     * parent Cell Species. It creates Reactions from the Device RuleLists and maps a newly created Device
     * Compartment to the parent Cell Compartment.
     * 
     * @param devices is a List of Devices.
     * @param cModel is the Devices' parent model.
     * @param cModelPlugin is the plugin of the Devices' parent model.
     * @param cBasePlugin is the plugin of the Compartment in the Devices' parent model.
     * @param compDoc is the plugin of the overall SBML document
     */
    private static void convertDevices(List<Device> devices, Model cModel, CompModelPlugin cModelPlugin, CompSBasePlugin cBasePlugin, CompSBMLDocumentPlugin compDoc) {
    	for (Device d : devices) {
			String deviceName = fixDisplayID(d.getDisplayName());
			Model dModel = compDoc.createModelDefinition(deviceName);
			dModel.setTimeUnits(Kind.SECOND);
			createUnits(dModel);
			CompModelPlugin dModelPlugin = getCompModelPlugin(dModel);
    		Submodel dSubmodel = new Submodel(deviceName + "_submodel", level, version);
    		dSubmodel.setModelRef(deviceName);
    		cModelPlugin.addSubmodel(dSubmodel);
			
			Compartment dCompartment = dModel.createCompartment(deviceName + "_compartment");
			dCompartment.setConstant(true);
    		dCompartment.setUnits("fL");
    		dCompartment.setSize(1.0);
			Port devicePort = dModelPlugin.createPort();
			devicePort.setId(deviceName + "_compartment_port");
			devicePort.setIdRef(deviceName + "_compartment");
			//Replace the device compartment with the cell compartment
			ReplacedElement re = cBasePlugin.createReplacedElement();
			re.setSubmodelRef(deviceName + "_submodel");
			re.setPortRef(deviceName + "_compartment_port");
			
			convertMolecules(d.getMoleculeList(), dModel, dCompartment);
			
			for (MolecularSpecies ms : d.getInputList()) {
				setReplacement(ms, fixDisplayID(ms.getDisplayName()) + "_input", deviceName + "_submodel", cModel, dModel, dCompartment, dModelPlugin);
			}
			for (MolecularSpecies ms : d.getOutputList()) {
				setReplacement(ms, fixDisplayID(ms.getDisplayName()) + "_output", deviceName + "_submodel", cModel, dModel, dCompartment, dModelPlugin);
			}
			
    		for (Rule r : d.getRuleList()) {
				setRule(r, dModel, dCompartment);
			}

		}
    }
    
    /**
     * This function parses and instantiates each MoleculeList, RuleList, and DeviceList of a group of Cells.
     * 
     * @param cells is a List of Cells.
     * @param bModelPlugin is the plugin of the Cells' parent Model.
     * @param compDoc is the plugin of the overall SBML Document.
     */
    private static void convertCells(List<Cell> cells, CompModelPlugin bModelPlugin, CompSBMLDocumentPlugin compDoc) {
		for (Cell c : cells) {
    		
			String cellName = fixDisplayID(c.getDisplayName());
    		Model cModel = compDoc.createModelDefinition(cellName);
    		cModel.setTimeUnits(Kind.SECOND);
    		createUnits(cModel);
    		Compartment cCompartment = cModel.createCompartment(cellName + "_compartment");
    		cCompartment.setConstant(true);
    		cCompartment.setUnits("fL");
    		cCompartment.setSize(1.0);
    		
    		CompModelPlugin cModelPlugin = getCompModelPlugin(cModel);
			CompSBasePlugin cBasePlugin = getCompSBasePlugin(cCompartment);
    		
    		Submodel cSubmodel = new Submodel(cellName + "_submodel", level, version);
    		cSubmodel.setModelRef(c.getDisplayName());
    		bModelPlugin.addSubmodel(cSubmodel);
    		
    		//DNA created in devices
    		for (MolecularSpecies ms : c.getMoleculeList()) {
    			if(ms.getBiologicalType() == "DNA") continue;
    			createSpecies(ms, fixDisplayID(ms.getDisplayName()) + "_molecule", cModel, cCompartment);
    		}
    		
    		for (Rule r : c.getRuleList()) {
    			setRule(r, cModel, cCompartment);
    		}
    		
    		convertDevices(c.getDeviceList(), cModel, cModelPlugin, cBasePlugin, compDoc);

    	}
    }
    
    /**
     * This function parses and instantiates the MoleculeList and/or the CellList of a group of Region objects.
     * 
     * @param regions is a List of Regions.
     * @param bModelPlugin is the plugin of the Regions' parent Model
     * @param compDoc is the plugin of the overall SBML Document
     */
    private static void convertRegions(List<Region> regions, CompModelPlugin bModelPlugin, CompSBMLDocumentPlugin compDoc) {
    	for (Region r : regions) {
    		
    		String regionName = fixDisplayID(r.getDisplayName());
    		Model rModel = compDoc.createModelDefinition(regionName);
    		rModel.setTimeUnits(Kind.SECOND);
    		createUnits(rModel);
    		CompModelPlugin rModelPlugin = getCompModelPlugin(rModel);
    		
    		Submodel rSubmodel = new Submodel(regionName + "_submodel", level, version);
    		rSubmodel.setModelRef(regionName);
    		bModelPlugin.addSubmodel(rSubmodel);
			List<MolecularSpecies> moleculeList = r.getMoleculeList();
			
			if (moleculeList != null && !moleculeList.isEmpty()) {
	    		Compartment rCompartment = rModel.createCompartment(regionName + "_compartment");
	    		rCompartment.setConstant(true);
	    		rCompartment.setUnits("fL");
	    		rCompartment.setSize(1.0);
				convertMolecules(moleculeList, rModel, rCompartment);
			}
			else convertCells(r.getCellList(), rModelPlugin, compDoc);
    
    	}
    }
    
    /**
     * This function reads the information from either a hierarchical or flattened EMF model into a SBML document.
     * 
     * @param model is the hierarchical EMF model
     * @param flatModel is the flattened EMF model
     * @return the constructed SBML document
     */
    public static SBMLDocument makeSBMLDocument(roadblock.emf.ibl.Ibl.Model model, FlatModel flatModel) {
		SBMLDocument doc = new SBMLDocument(level, version);
		doc.enablePackage(CompConstants.namespaceURI);
		CompSBMLDocumentPlugin compDoc = getCompDocPlugin(doc);
		String modelName = (model != null ? fixDisplayID(model.getDisplayName()) : "Flat_Model");
		Model bioModel = doc.createModel(modelName);
		bioModel.setTimeUnits(Kind.SECOND);
		createUnits(bioModel);
		
		CompModelPlugin bModelPlugin = getCompModelPlugin(bioModel);
    	
		//Flat model creation
		if (model == null) {
    		Compartment bCompartment = bioModel.createCompartment("Flat_Model_compartment");
    		bCompartment.setConstant(true);
			convertMolecules(flatModel.getMoleculeList(), bioModel, bCompartment);
			for (Rule r : flatModel.getRuleList()) {
				setRule(r, bioModel, bCompartment);
			}
			return doc;
		}
		
    	List<Region> regions = model.getRegionList();
    	List<Cell> cells = model.getCellList();
    	List<Device> devices = model.getDeviceList();
    	List<MolecularSpecies> molecules = model.getMoleculeList();
    	
    	if (regions != null && !regions.isEmpty()) {
    		convertRegions(regions, bModelPlugin, compDoc);
    	}
    	else if (cells != null && !cells.isEmpty()) {
    		convertCells(cells, bModelPlugin, compDoc);
    	}
    	else {
    		//Compartment only needed to contain devices or molecules
    		Compartment cCompartment = bioModel.createCompartment(modelName + "_compartment");
    		cCompartment.setConstant(true);
			CompSBasePlugin cBasePlugin = getCompSBasePlugin(cCompartment);
        	if (devices != null && !devices.isEmpty()) {
        		convertDevices(devices, bioModel, bModelPlugin, cBasePlugin, compDoc);
        	}
        	else {
        		convertMolecules(molecules, bioModel, cCompartment);
        	}
    	}
		
		ID = 1;
    	return doc;
    	
    }
}