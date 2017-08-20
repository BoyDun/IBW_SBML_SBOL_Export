package roadblock.dataprocessing.util;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.xml.stream.XMLStreamException;

import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLError;
import org.sbml.jsbml.SBMLException;
import org.sbml.jsbml.SBMLWriter;
import org.sbml.jsbml.SBase;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.UnitDefinition;
import org.sbml.jsbml.ext.comp.CompConstants;
import org.sbml.jsbml.ext.comp.CompModelPlugin;
import org.sbml.jsbml.ext.comp.CompSBMLDocumentPlugin;
import org.sbml.jsbml.ext.comp.CompSBasePlugin;
import org.sbml.jsbml.ext.comp.Port;
import org.sbml.jsbml.ext.comp.ReplacedBy;
import org.sbml.jsbml.ext.comp.ReplacedElement;
import org.sbml.jsbml.ext.comp.Submodel;

import roadblock.emf.bioparts.Bioparts.Biopart;
import roadblock.emf.ibl.Ibl.Cell;
import roadblock.emf.ibl.Ibl.Device;
import roadblock.emf.ibl.Ibl.MolecularSpecies;
import roadblock.emf.ibl.Ibl.Rule;

public class SBML_Export {
    
    private static CompSBMLDocumentPlugin getCompDocPlugin(SBMLDocument doc) {
		CompSBMLDocumentPlugin compDoc = new CompSBMLDocumentPlugin(doc);
		doc.addExtension(CompConstants.namespaceURI, compDoc);
		return compDoc;
    }
    
    private static CompModelPlugin getCompModelPlugin(Model model) {
    	CompModelPlugin compModel = new CompModelPlugin(model);
    	model.addExtension(CompConstants.namespaceURI, compModel);
    	return compModel;
    }
    
    private static CompSBasePlugin getCompSBasePlugin(SBase sb) {
    	CompSBasePlugin compBase = new CompSBasePlugin(sb);
    	sb.addExtension(CompConstants.namespaceURI, compBase);
    	return compBase;
    }
    
    private static Species createSpecies(MolecularSpecies ms, String displayID, Model model, Compartment compartment) {
		Species species = model.createSpecies(displayID, compartment);
		if (ms.getAmount() != 0) species.setInitialAmount(ms.getAmount());
		//create unit definition for any unit other than "item" (numebr of molecules) and mole. 
		//Go through IBW predefined list of units, create unit definitions for those
		if(ms.getUnit() != null) {
			String unit = ms.getUnit().getLiteral();
			if(model.findUnitDefinition(unit) == null) model.createUnitDefinition(unit);
		}
		return species;
    }
    
    private static void setReplacement(MolecularSpecies ms, String displayId, String submodelRef, Model model, Compartment compartment, int level, int version) {
		String mName = ms.getDisplayName();
    	if (model.containsSpecies(mName + "_molecule")) {
			Species replacementSpecies = model.getSpecies(mName + "_molecule");
			CompSBasePlugin speciesPlugin = getCompSBasePlugin(replacementSpecies);
			
			Species replacedSpecies = model.createSpecies(displayId, compartment);
			Port speciesPort = new Port(mName + "_port", level, version);
			speciesPort.setIdRef(displayId);
			
			ReplacedElement reSpecies = speciesPlugin.createReplacedElement();
			reSpecies.setSubmodelRef(submodelRef);
			reSpecies.setPortRef(mName + "_port");
		}
		else{
			createSpecies(ms, displayId, model, compartment);
		}
    }
    
    private static void setRule(Rule r, Model model, Compartment compartment) {
		Reaction reaction = model.createReaction(r.getDisplayName());
		reaction.setCompartment(compartment);
		for(MolecularSpecies ms : r.getLeftHandSide()) {
			String mName = ms.getDisplayName() + "_molecule";
			if (!model.containsSpecies(mName)) createSpecies(ms, mName, model, compartment);
			reaction.createReactant(model.getSpecies(mName));
		}
		for(MolecularSpecies ms : r.getRightHandSide()) {
			String mName = ms.getDisplayName() + "_molecule";
			if (!model.containsSpecies(mName)) createSpecies(ms, mName, model, compartment);
			reaction.createProduct(model.getSpecies(mName));
		}
		if (r.isIsBidirectional()) reaction.setReversible(true);
		//set kinetic law. HOW TO MODEL NUMBERS
		//set sboterm??
    }
    
    public static SBMLDocument makeSBMLDocument(roadblock.emf.ibl.Ibl.Model model) {
    	
    	int version = 1;
    	int level = 3;
    	
		SBMLDocument doc = new SBMLDocument(level, version);
		doc.enablePackage(CompConstants.namespaceURI);
		CompSBMLDocumentPlugin compDoc = getCompDocPlugin(doc);
    	Model bioModel = compDoc.createModelDefinition(model.getDisplayName());
		CompModelPlugin bModelPlugin = getCompModelPlugin(bioModel);
    	
		for (Cell c : model.getCellList()) {
    		
    		Model cModel = compDoc.createModelDefinition(c.getDisplayName());
    		Compartment cCompartment = cModel.createCompartment(c.getDisplayName() + "_compartment");
    		CompModelPlugin cModelPlugin = getCompModelPlugin(cModel);
			CompSBasePlugin cBasePlugin = getCompSBasePlugin(cCompartment);
    		
    		Submodel cSubmodel = new Submodel(c.getDisplayName() + "_submodel", level, version);
    		cSubmodel.setModelRef(c.getDisplayName());
    		bModelPlugin.addSubmodel(cSubmodel);
    		
//    		for (MolecularSpecies ms : c.moleculeList) { /* Have it ignore DNA parts? */
//    			createSpecies(ms, ms.name + "_molecule", cModel, cCompartment);
//    		}
    		
//    		for (Rule r : c.ruleList) {
//    			setRule(r, cModel, cCompartment);
//    		}
    		
    		for (Device d : c.getDeviceList()) {
    			String deviceName = d.getDisplayName();
    			Model dModel = compDoc.createModelDefinition(deviceName);
        		Submodel dSubmodel = new Submodel(deviceName + "_submodel", level, version);
        		dSubmodel.setModelRef(deviceName);
        		cModelPlugin.addSubmodel(dSubmodel);
    			
    			Compartment dCompartment = dModel.createCompartment(deviceName + "_compartment");
    			Port devicePort = new Port(deviceName + "_port", level, version);
//    			devicePort.setSBaseRef(dCompartment); SET ID REF
    			ReplacedElement re = cBasePlugin.createReplacedElement();
    			re.setSubmodelRef(deviceName + "_submodel");
    			re.setPortRef(deviceName + "_port");
    			
    			String partName = "DNA";
    			ArrayList<Biopart> allParts = d.parts;
				Collections.sort(allParts, new Comparator<Biopart>() {
					public int compare(Biopart a, Biopart b) {
						return a.getPosition().value() > b.getPosition().value() ? 1 : -1;
					}
				});  
    			for (Biopart part : allParts) {
    				partName += "_" + part.name;
    			}
    			//Give DNA name device name_DNA.
    			//Create species for DNA part that appears in rule
    			dModel.createSpecies(partName, dCompartment);
    			//Create map of each species and how many times it occurs on left and right hand sides.
    			//If it appears equal numbers on left and right, its a modifier.
    			//If it appears more on left than right, its a reactant. Stoichiometry is left - right.
    			//Same for more on right than left.
    			//Kinetic law is take forward rate times each of reactants you found, raised to the power of its stoichiometry. If it is bidirectional, incorporate backwards reaction too. Forward - reverse rate.
    			
        		for (MolecularSpecies ms : d.getMoleculeList()) { /* Have it ignore DNA parts? */
    				createSpecies(ms, ms.getDisplayName() + "_molecule", dModel, dCompartment);
    			}
    			
    			for (MolecularSpecies ms : d.getInputList()) {
    				setReplacement(ms, ms.getDisplayName() + "_input", deviceName + "_submodel", dModel, dCompartment, level, version);
    			}
    			for (MolecularSpecies ms : d.getOutputList()) {
    				setReplacement(ms, ms.getDisplayName() + "_output", deviceName + "_submodel", dModel, dCompartment, level, version);
    			}
    			
        		for (Rule r : d.getRuleList()) {
    				setRule(r, dModel, dCompartment);
    			}

    		}

    	}
//		System.out.println(doc.checkConsistency());
//		System.out.println(doc.getListOfErrors()); //Which one is right error checking?
    	JFrame frame = new JFrame();
    	int choice = JOptionPane.showConfirmDialog(
    		    frame,
    		    "Do you want to flatten the exported SBML document?",
    		    "SBML Flattening",
    		    JOptionPane.YES_NO_OPTION);
    	frame.dispose();
    	if (choice == JOptionPane.YES_OPTION) {};
    	return doc;
    	
    }
}
