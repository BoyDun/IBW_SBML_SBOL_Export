package roadblock.biocompiler

import com.almworks.sqlite4java.SQLiteConnection
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.util.ArrayList
import java.util.Date
import java.util.List
import org.apache.commons.io.IOUtils
import org.eclipse.core.runtime.Platform
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtend.lib.annotations.Data
import org.jacop.constraints.Alldifferent
import org.jacop.constraints.And
import org.jacop.constraints.Max
import org.jacop.constraints.Min
import org.jacop.constraints.Or
import org.jacop.constraints.PrimitiveConstraint
import org.jacop.constraints.XeqC
import org.jacop.constraints.XgtY
import org.jacop.constraints.XltY
import org.jacop.constraints.XplusCeqZ
import org.jacop.core.BooleanVar
import org.jacop.core.IntVar
import org.jacop.core.Store
import org.jacop.search.DepthFirstSearch
import org.jacop.search.IndomainMin
import org.jacop.search.InputOrderSelect
import org.jacop.search.Search
import org.jacop.search.SelectChoicePoint
import roadblock.bin.BinaryPathProvider
import roadblock.biocompiler.util.BiocompilerUtil
import roadblock.biocompiler.util.EncodedImages
import roadblock.emf.bioparts.Bioparts.BiocompilerCell
import roadblock.emf.bioparts.Bioparts.BiocompilerDevice
import roadblock.emf.bioparts.Bioparts.Biopart
import roadblock.emf.bioparts.Bioparts.BiopartsFactory
import roadblock.emf.ibl.Ibl.ATGCArrange
import roadblock.emf.ibl.Ibl.ATGCCloningSites
import roadblock.emf.ibl.Ibl.ATGCDirection
import roadblock.emf.ibl.Ibl.ATGCTranslationRate
import roadblock.emf.ibl.Ibl.Cell
import roadblock.emf.ibl.Ibl.Device
import roadblock.emf.ibl.Ibl.IblFactory
import roadblock.emf.ibl.Ibl.Model
import roadblock.emf.ibl.Ibl.MolecularSpecies
import roadblock.emf.ibl.Ibl.Region
import org.sbolstandard.core2.SBOLReader
import org.synbiohub.frontend.SynBioHubFrontend


@Data class RestrictionEnzyme {
	String name
	String sequence
}

@Data class Interval {
	Integer x1
	Integer x2
}

class NoArrangementFound extends Exception {
}

class RBSOptimisationIssue extends Exception {
}

class MalFormedURI extends Exception {
	public String partName
	public String partURI

	new(String partName, String partURI) {
		this.partName = partName
		this.partURI = partURI
	}
}

class UnknownPartInDatabase extends Exception {
	public String partName
	public String collection

	new(String partName, String collection) {
		this.partName = partName
		this.collection = collection
	}
}

class UnknownPartInPartRegistry extends Exception {
	public String partName

	new(String partName) {
		this.partName = partName
	}
}

class UnknownPartInVirtualPartRepository extends Exception {
	public String partName

	new(String partName) {
		this.partName = partName
	}
}

class MalFormedDNASequence extends Exception {
	public String partName
	public String partSequence

	new(String partName, String partSequence) {
		this.partName = partName
		this.partSequence = partSequence
	}
}

class Biocompiler {
	val static utils = new BiocompilerUtil

	val Model model

	var biopartsFactory = BiopartsFactory::eINSTANCE
	var public biocompilerModel = biopartsFactory.createBiocompilerModel
	var public store = new Store

	var HtmlLog log = new HtmlLog(1000)

	var modelFactory = IblFactory::eINSTANCE

	def static void execute(String filename) {

		println("loading the EMF data model" + filename)

		val String biocompilationDirectory = String.format("%s%s%s%s%s", File.separator, ".tmp", File.separator,
			"biocompilation", File.separator);
		val String workspacePath = Platform.getLocation().toString() + biocompilationDirectory;

		val XMLsource = utils.readFile(filename, Charset.defaultCharset())
		println("EMF model read")

		val emfModel = utils.convertToEObject(XMLsource) as Model
		println("EMF model parsed")

		var biocompiler = new Biocompiler(emfModel)
		println("biocompiler instantiated and populated")

		biocompiler.gatherParts
		println("part gathering done.")

		// write biocompile.resultsHTml
		utils.toFile(workspacePath + "identifiedParts.html", biocompiler.identifiedPartsHtml)

		biocompiler.compile
		println("Compilation done.")

		// write biocompile.console
		utils.toFile(workspacePath + "console.html", biocompiler.makeHtmlLog)

		// write biocompiler results
		utils.toFile(workspacePath + "results.html", biocompiler.makeResultPage)

	}

	new(Model emfModel) {
		println(new Date())
		println("ATGC: Compiling")
		model = emfModel
		log.addText(model.toString)
		log.addText('ATGC: new instance')
	}

	def boolean compile() { // the whole process, done after gathering parts
		try {
			lookUpSequence

			// complete devices
			completeDevices
			createIntVarForAllparts
			constraintAllDifferent
			constraintNonOverlapping
			constraintPositionByType
			constraintATGCARRANGE
			constraintATGCDIRECTION

			//
			println("===========")
			println("Find arrangement")

			findArrangement // throws NoArrangementFound
			println("===========")
			addStartCodonToCDS
			findRBSSequence
			findTerminatorSequence

			// findNoncuttingRestrictionEnzymes
			for (cell : biocompilerModel.cells) {
				var ref = new RestrictionEnzymesFinder(cell, "b")
				log.addLog(ref.searchRE)
			}

		} catch (MalFormedDNASequence e) {
			log.addError("The sequence you specified is not made of A, T, G or C only.")
			log.addError("\t Part: " + e.partName)
			log.addError("\t Sequence: " + e.partSequence)
			return false
		} catch (MalFormedURI e) {
			log.addError("There was a problem when looking up the sequence of the following part:" + e.partName)
			log.addError(
				"\tURI (" + e.partURI +
					") is malformed. It should be one of atgc://biofab/part/, atgc://user-submitted/part/, http://parts.igem.org/part: or http://sbol.ncl.ac.uk:8081/part/"
			)
			return false
		} catch (UnknownPartInDatabase e) {
			log.addError(
				"There was a problem when looking up the part:" + e.partName +
					" in the built-in database (collection: " + e.collection + ").")
			return false
		} catch (UnknownPartInPartRegistry e) {
			log.addError("There was a problem when looking up the part:" + e.partName +
				" in Part Registry. Please double-check the URI.")
			return false

		} catch (UnknownPartInVirtualPartRepository e) {
			log.addError("There was a problem when looking up the part:" + e.partName +
				" in Virtual Part Repository. Please double-check the URI.")
			return false

		} catch (NoArrangementFound e) {
			log.addError('No possible arrangement was possible.')
			return false
		} catch (RBSOptimisationIssue e) {
			log.addError('Problem with the RBS calculator.')
			return false
		} catch (Exception e) {
			log.addError("Something went wrong. Please contact the author.")
			log.addError("Error: " + e.message)
			e.printStackTrace
			return false
		}

		log.addWarning("Compilation successful")
		return true
	}

	def gatherParts() {
		log.addText("Gathering parts.")
		for (region : model.regionList) {
			log.addText("\t\tregion:" + region.displayName)
			for (cell : region.cellList) {
				var biocompilerCell = biopartsFactory.createBiocompilerCell
				biocompilerCell.name = cell.displayName
				log.addText("\t\tcell: " + cell.displayName)
				for (device : cell.deviceList) {
					var biocompilerDevice = biopartsFactory.createBiocompilerDevice
					biocompilerDevice.name = device.displayName
					log.addText("\t\t\tDevice:" + device.displayName)
					for (partName : device.partList.map[displayName]) {
						log.addText("\t\t\t\tPart: " + partName)

						// look up the part's declaration
						val part = searchFirstDeclaration(device, partName);

						// create a biopart with relevant info 
						var biopart = biopartsFactory.createBiopart
						biopart => [
							name = part.displayName
							sequence = part.sequence
							biologicalFunction = part.biologicalType
							cellName = cell.displayName
							deviceName = device.displayName
							accessionURL = part.URI
						]

						// add to device
						biocompilerDevice.parts.add(biopart)
					}

					// adding cloning sites if need be
					var commands = device.ATGCCommandList.filter[it.class == ATGCCloningSites]
					if (!commands.empty) {
						var n = commands.map[(it as ATGCCloningSites).cloningSites].reduce[a, b|a + b]
						if (n > 0)
							for (k : 1 .. n) {
								var biopart = biopartsFactory.createBiopart
								biopart => [
									name = cell.displayName + "/" + device.displayName + "/RE_" + k
									sequence = ''
									biologicalFunction = 'CLONINGSITE'
									cellName = cell.displayName
									deviceName = device.displayName
									accessionURL = null
								]

								// add to device
								biocompilerDevice.parts.add(biopart)
							}
					}

					// setting translation rate
					commands = device.ATGCCommandList.filter[it.class == ATGCTranslationRate]
					if (!commands.empty) {
						biocompilerDevice.translationRate = (commands.last as ATGCTranslationRate).translationRate
					}

					biocompilerCell.devices.add(biocompilerDevice)

				}
				biocompilerModel.cells.add(biocompilerCell)
			}
		}
		println(utils.convertToXml(biocompilerModel))
		log.addText("Parts gathered.")
	}

	def completeDevices() {
		log.addText("Completing devices.")
		for (cell : biocompilerModel.cells) {
			for (device : cell.devices) {

				// add 1 RBS per gene
				val numberOfGenes = device.parts.filter[biologicalFunction == 'GENE'].size
				for (k : 1 .. numberOfGenes) {
					var biopart = biopartsFactory.createBiopart
					biopart => [
						name = cell.name + "/" + device.name + "/rbs_" + k
						sequence = ""
						biologicalFunction = "RBS"
						cellName = cell.name
						deviceName = device.name
						accessionURL = ""
					]
					device.parts.add(biopart)
				}

				// add 1 terminator if necessary (i.e. if none have been set by the user)				
				// if(device.parts.filter[biologicalFunction == 'TERMINATOR'].size == 0){
				var biopart = biopartsFactory.createBiopart
				biopart => [
					name = cell.name + "/" + device.name + "/terminator"
					sequence = ""
					biologicalFunction = "TERMINATOR"
					cellName = cell.name
					deviceName = device.name
					accessionURL = ""
				]
				device.parts.add(biopart)

			// }				
			}

		}
		log.addText("Completing devices is done.")

		println(biocompilerModel)
	}

	def createIntVarForAllparts() {

		// get the number of parts
		var partsNumber = 0
		for (cell : biocompilerModel.cells)
			for (device : cell.devices)
				partsNumber = partsNumber + device.parts.size

		var count = 0;
		for (cell : biocompilerModel.cells)
			for (device : cell.devices) {
				val path = cell.name + "/" + device.name + "/"
				for (part : device.parts) {
					count = count + 1
					part.position = new IntVar(store, path + part.name + "_" + count, 1, partsNumber)
				}

				device.direction = new BooleanVar(store, path + "direction")
				device.minPosition = new IntVar(store, path + "minPosition", 1, partsNumber)
				device.maxPosition = new IntVar(store, path + "maxPosition", 1, partsNumber)
				store.impose(new Min(device.parts.map[position], device.minPosition))
				store.impose(new Max(device.parts.map[position], device.maxPosition))
			}

		println(utils.convertToXml(biocompilerModel))
	}

	def constraintAllDifferent() {
		val ArrayList<IntVar> allPartPositions = new ArrayList()
		for (cell : biocompilerModel.cells)
			for (device : cell.devices)
				allPartPositions.addAll(device.parts.map[position])

		store.impose(new Alldifferent(allPartPositions))
	}

	def constraintNonOverlapping() {
		for (cell : biocompilerModel.cells) {
			val devicesNumber = cell.devices.size
			if (devicesNumber > 1) {
				var ArrayList<PrimitiveConstraint> nonOverlappingConstraints = newArrayList()
				for (combination : allCombinations(devicesNumber)) {
					var ArrayList<PrimitiveConstraint> configurationIndividualConstraints = newArrayList()
					for (k : 1 .. (devicesNumber - 1)) {
						val k1 = combination.get(k - 1)
						val k2 = combination.get(k)
						configurationIndividualConstraints.add(
							new XltY(cell.devices.get(k1 - 1).maxPosition, cell.devices.get(k2 - 1).minPosition))
					}
					nonOverlappingConstraints.add(new And(configurationIndividualConstraints))

				}
				store.impose(new Or(nonOverlappingConstraints))
			}
		}

	}

	def constraintPositionByType() {
		for (cell : biocompilerModel.cells)
			for (device : cell.devices) {

				// promoter < rbs
				for (promoter : device.parts.filter[biologicalFunction == "PROMOTER"].map[position])
					for (rbs : device.parts.filter[biologicalFunction == "RBS"].map[position])
						store.impose(
							new Or(
								new And(new XltY(promoter, rbs), new XeqC(device.direction, 1)),
								new And(new XgtY(promoter, rbs), new XeqC(device.direction, 0))
							)
						)

				// contiguous RBS and GENE
				val rbsList = device.parts.filter[biologicalFunction == "RBS"].map[position]
				val geneList = device.parts.filter[biologicalFunction == "GENE"].map[position]

				for (k : ( 1 .. rbsList.size)) {
					store.impose(
						new Or(
							new And(new XplusCeqZ(rbsList.get(k - 1), 1, geneList.get(k - 1)),
								new XeqC(device.direction, 1)),
							new And(new XplusCeqZ(geneList.get(k - 1), 1, rbsList.get(k - 1)),
								new XeqC(device.direction, 0))
						)
					)
				}

				// gene < terminator
				for (gene : device.parts.filter[biologicalFunction == "GENE"].map[position])
					for (terminator : device.parts.filter[biologicalFunction == "TERMINATOR"].map[position])
						store.impose(
							new Or(
								new And(new XltY(gene, terminator), new XeqC(device.direction, 1)),
								new And(new XgtY(gene, terminator), new XeqC(device.direction, 0))
							)
						)

				// GENE < CLONING SITES
				for (gene : device.parts.filter[biologicalFunction == "GENE"].map[position])
					for (cloningSite : device.parts.filter[biologicalFunction == "CLONINGSITE"].map[position])
						store.impose(
							new Or(
								new And(new XltY(gene, cloningSite), new XeqC(device.direction, 1)),
								new And(new XgtY(gene, cloningSite), new XeqC(device.direction, 0))
							)
						)

				// CLONING SITES < TERMINATOR 				
				for (cloningSite : device.parts.filter[biologicalFunction == "CLONINGSITE"].map[position])
					for (terminator : device.parts.filter[biologicalFunction == "TERMINATOR"].map[position])
						store.impose(
							new Or(
								new And(new XltY(cloningSite, terminator), new XeqC(device.direction, 1)),
								new And(new XgtY(cloningSite, terminator), new XeqC(device.direction, 0))
							)
						)
			}

	}

	def findPart(String cellName, String deviceName, String partName) {
		return biocompilerModel.cells.findFirst[name == cellName]?.devices.findFirst[name == deviceName]?.parts.
			findFirst[name == partName]
	}

	def findPart(String cellName, String partName) {
		val cell = biocompilerModel.cells.findFirst[name == cellName]
		for (device : cell.devices) {
			var part = device.parts.findFirst[name == partName]
			if(part != null) return part
		}
		return null
	}

	def findDevice(String cellName, String deviceName) {
		return biocompilerModel.cells.findFirst[name == cellName]?.devices.findFirst[name == deviceName]
	}

	def static direction(Biopart part) { // look up direction of containing device
		return (part.eContainer as BiocompilerDevice).direction.value
	}

	def constraintATGCARRANGE() {
		log.addText("Adding ARRANGE Constraints")
		for (region : model.regionList)
			for (cell : region.cellList) {
				println("at cell level")

				// at cell level
				for (arrange : cell.ATGCCommandList.filter[class == ATGCArrange].map[it as ATGCArrange]) {
					val partList = arrange.partList.map[displayName]
					for (k : 1 .. (partList.size - 1)) {
						println("\tk = " + k)
						val part1 = findPart(cell.displayName, partList.get(k - 1))
						val part2 = findPart(cell.displayName, partList.get(k))
						store.impose(new XltY(part1.position, part2.position))
					}
				}
				for (device : cell.deviceList) {

					// at device level
					println("at device level")
					for (arrange : device.ATGCCommandList.filter[class == ATGCArrange].map[it as ATGCArrange]) {
						val partList = arrange.partList.map[displayName]
						for (k : 1 .. (partList.length - 1)) {
							val part1 = findPart(cell.displayName, device.displayName, partList.get(k - 1))
							val part2 = findPart(cell.displayName, device.displayName, partList.get(k))
							store.impose(new XltY(part1.position, part2.position))
						}
					}
				}
			}
	}

	def constraintATGCDIRECTION() {
		log.addText("Adding DIRECTION Constraints")

		for (region : model.regionList)
			for (cell : region.cellList) {
				for (device : cell.deviceList) {
					for (command : device.ATGCCommandList.filter[class == ATGCDirection].map[it as ATGCDirection]) {
						val biocompilerDevice = findDevice(cell.displayName, device.displayName)
						println("ATGC DIRECTION: Found one in " + cell.displayName + "/" + device.displayName)
						println("\t Device in biocompilerDevice:" + biocompilerDevice)
						println("\t direction:" + command.direction)
						if (biocompilerDevice != null)
							store.impose(
								new XeqC(biocompilerDevice.direction, if(command.direction == 'BACKWARD') 0 else 1))
					}
				}
			}
	}

	def allCombinations(Integer n) { // produces all combinations of n items
		var combinations = newArrayList(newArrayList(1))

		for (k : ( 2 .. n)) {
			var temp = newArrayList()
			for (c : combinations)
				for (position : 0 .. c.size) {
					var temp2 = newArrayList()
					temp2.addAll(c)
					temp2.add(position, k)
					temp.add(temp2)
				}
			combinations = temp
		}

		combinations
	}

	def findArrangement() throws NoArrangementFound {
		val ArrayList<IntVar> allPartPositions = new ArrayList()
		for (cell : biocompilerModel.cells)
			for (device : cell.devices)
				allPartPositions.addAll(device.parts.map[position])

		// search for a solution and print results 
		var Search<IntVar> search = new DepthFirstSearch<IntVar>();
		var SelectChoicePoint<IntVar> select = new InputOrderSelect<IntVar>(store, allPartPositions,
			new IndomainMin<IntVar>());
		var boolean result = search.labeling(store, select);

		if(!result) throw new NoArrangementFound

		for (cell : biocompilerModel.cells) {

			// gather all parts in that cell
			val ArrayList<Biopart> allParts = new ArrayList()
			for (device : cell.devices)
				allParts.addAll(device.parts)
			println(allParts.sortBy[position.value].map[name])
		}

	}

	def lookUpSequence() {

		// look up sequences in built-in database or online database
		log.addText("Looking up sequences")
		var ArrayList<Biopart> allParts = new ArrayList()
		for (cell : biocompilerModel.cells)
			for (device : cell.devices)
				allParts.addAll(device.parts.filter[#['PROMOTER', 'GENE'].contains(biologicalFunction)])

		val builtinBiofab = 'atgc://biofab/part/'
		val builtinUser = 'atgc://user-submitted/part/'
		val partsregistry = 'http://parts.igem.org/part:'
		val ncl = 'http://sbol.ncl.ac.uk:8081/part/'
		val synbiohub = 'https://synbiohub.org/'
		for (part : allParts) {
			log.addText("Looking up sequence for " + part.name)
			if (part.sequence == null || part.sequence == '') {
				val url = part.accessionURL

				// look up sequence from URI
				switch (url) {
					case url.toLowerCase.startsWith(builtinBiofab): {
						part.sequence = getSequenceFromDatabase(url.substring(builtinBiofab.length), 'biofab')
					}
					case url.toLowerCase.startsWith(builtinUser): {
						part.sequence = getSequenceFromDatabase(url.substring(builtinUser.length), 'user-submitted')
					}
					case url.toLowerCase.startsWith(partsregistry): {
						part.sequence = getSequenceFromPartsRegistry(url.substring(partsregistry.length))
					}
					case url.toLowerCase.startsWith(ncl): {
						part.sequence = getSequenceFromNCL(url.substring(ncl.length))
					}
					case url.toLowerCase.startsWith(synbiohub): {
						part.sequence = getSequenceFromSynbiohub(url)
					}
					default: {
						throw new MalFormedURI(part.name, part.accessionURL)
					}
				}
			} else {

				// check sequence is made of ATGC
				if (utils.isValidDNASequence(part.sequence))
					part.accessionURL = 'ATGC://user-submitted/seq#' + part.sequence
				else
					throw new MalFormedDNASequence(part.name, part.sequence)
			}

		}
	}

	def private getSequenceFromDatabase(String partName, String collection) {

		// pick some from the DB
		val databaseLocation = BinaryPathProvider.getInstance().partRegistryDbPath
		var db = new SQLiteConnection(new File(databaseLocation))
		if(!db.isOpen) db.open()

		var sql = db.prepare(
			"SELECT sequence FROM partRegistry WHERE LOWER(name) = '" + partName.toLowerCase + "' AND Origin ='" +
				collection + "'")

		var sequence = if (sql.step)
				sql.columnString(0).toLowerCase
			else
				throw new UnknownPartInDatabase(partName, collection)

		// tidying up
		sql.dispose
		db.dispose

		return sequence
	}

	def private static getSequenceFromPartsRegistry(String partName) {

		var InputStream url
		var String result
		try {
			url = new URL('http://parts.igem.org/fasta/parts/' + partName).openStream
			result = IOUtils.toString(url)
			IOUtils.closeQuietly(url)
		} catch (FileNotFoundException e) {
			throw new UnknownPartInPartRegistry(partName)
		}

		return result.substring(result.indexOf("\n")).replace("\n", "")
	}

	def private static getSequenceFromNCL(String partName) {

    	var String sequence = ""
    	try {
    		var url = new URL("http://sbol.ncl.ac.uk:8081/part/" + partName + "/sbol").openStream()
    		var sbol = SBOLReader.read(url)
    		var sequences = sbol.getSequences()
    		sequence = sequences.iterator().next().getElements()
    	} catch (Exception e) {
    		System.out.println(e)
    		throw new UnknownPartInVirtualPartRepository(partName)
    	}

    	return sequence
	}

	def private static getSequenceFromSynbiohub(String partName) {
		
		var String sequence = ""
		try {
			val front = new SynBioHubFrontend('https://synbiohub.org/')
			var sbol = front.getSBOL(new URI(partName))
			var sequences = sbol.getSequences()
			sequence = sequences.iterator().next().getElements()
		} catch (Exception e) {
			System.out.println(e)
			throw new UnknownPartInVirtualPartRepository(partName)
		}

		return sequence
		
	}

	def addStartCodonToCDS() { // add a start codon to GENEs if not present
	// valid start codons: 'ATG' 'GTG'
		for (cell : biocompilerModel.cells)
			for (device : cell.devices)
				for (part : device.parts.filter[biologicalFunction == 'GENE'])
					if (!#['ATG', 'GTG'].contains(part.sequence.substring(0, 2).toUpperCase))
						part.sequence = 'ATG' + part.sequence

	}

	def buildWholeSequence(BiocompilerCell cell) {
		cell.devices.map[parts].flatten.sortBy[position.value].map[utils.finalSequence(it)].join
	}

	def boolean sequenceIsSet(Biopart part) { // checks if sequence has been set or not
		!(part.sequence == null || part.sequence == '')
	}

	def findTerminatorSequence() {

		// how many needed
		var numberTerminator = 0
		for (cell : biocompilerModel.cells)
			for (device : cell.devices)
				numberTerminator = numberTerminator + device.parts.filter[it.biologicalFunction == 'TERMINATOR'].length

		// pick some from the DB
		val databaseLocation = BinaryPathProvider.getInstance().partRegistryDbPath
		var db = new SQLiteConnection(new File(databaseLocation))
		if(!db.isOpen) db.open()

		var sql = db.prepare(
			"SELECT * FROM partRegistry WHERE BiologicalFunction=='terminator' ORDER BY RANDOM() LIMIT " +
				numberTerminator)

				for (cell : biocompilerModel.cells)
					for (device : cell.devices)
						for (part : device.parts.filter[biologicalFunction == 'TERMINATOR']) {
							sql.step
							part.sequence = sql.columnString(3)
							part.accessionURL = 'http://roadblock.com/atgc/terminator/computerGenerated/seq#' +
								part.sequence
							part.name = sql.columnString(1)
						}

				// tidying up
				sql.dispose
				db.dispose

			}

			def findRBSSequence() {

				// RBS is optimised by Salis' RBS calculator
				for (cell : biocompilerModel.cells)
					for (device : cell.devices)
						for (part : device.parts.filter[biologicalFunction == 'RBS']) {
							val tmp = utils.optimiseRBS(part, device.translationRate)
							part => [sequence = tmp.sequence accessionURL = tmp.accessionURL]
						}
			}

			// helper to find first declaration of variable given its displayName from local container upwards
			def MolecularSpecies searchFirstDeclaration(EObject container, String displayName) {

				// look among molecularSpecies at current level
				// return it if found
				switch (container) {
					Device: {
						val molecules = container.moleculeList.filter[it.displayName == displayName];
						if(!molecules.empty) return molecules.head
					}
					Cell: {
						val molecules = container.moleculeList.filter[it.displayName == displayName];
						if(!molecules.empty) return molecules.head
					}
					Region: {
						val molecules = container.moleculeList.filter[it.displayName == displayName];
						if(!molecules.empty) return molecules.head
					}
					Model: { // shouldn't happen: variable would have been declared somewhere before that. Unless the validator let it through.
						var molecule = modelFactory.createMolecularSpecies
						molecule.displayName = 'UNKNOWN REFERENCE: ' + displayName
						molecule.biologicalType = 'UNKNOWN'
						return molecule
					}
				}

				// else search in next container
				return searchFirstDeclaration(container.eContainer, displayName)
			}

			def void print() {
				for (cell : biocompilerModel.cells) {
					println("Cell: " + cell.name)
					for (device : cell.devices) {
						println("\tDevice: " + device.name)
						for (part : device.parts.sortBy[position.value])
							println("\t\t " + part.name + " ( " + part.biologicalFunction + " ) : " + part.sequence)

					}

				}

			}

			def String identifiedPartsHtml() {
				var content = <String>newArrayList
				content.add("<HTML>")
				content.add("<BODY BGCOLOR='#FCFCF0' STYLE='font-size:12px'>")
				content.add("<TT>Generated on: " + (new Date) + "</TT>")
				var template = '''
					«FOR cell : biocompilerModel.cells»
						<H2>CELL: «cell.name»</H2>
						«FOR device : cell.devices»
							<H3>Device: «device.name»</H3>
							<UL>
							«FOR part : device.parts»
								<LI>Part: «part.name» («part.biologicalFunction»)</LI>
							«ENDFOR»
							</UL>
						«ENDFOR»
					«ENDFOR»
				'''
				content.add(template)
				content.add("</BODY>")
				content.add("</HTML>")
				return content.join('\n')
			}

			def makeResultPage() {
				val List<String> colours = newArrayList
				colours.add("#A6611A")
				colours.add("#DFC27D")
				colours.add("#F5F5F5")
				colours.add("#80CDC1")
				colours.add("#018571")
				val encodedImages = new EncodedImages
				val imageNames = <String, String>newHashMap // from type+direction to image filename
				imageNames.put("promoter0", "data:image/png;base64," + encodedImages.promoterReversed)
				imageNames.put("promoter1", "data:image/png;base64," + encodedImages.promoter)
				imageNames.put("rbs0", "data:image/png;base64," + encodedImages.rbsReversed)
				imageNames.put("rbs1", "data:image/png;base64," + encodedImages.rbs)
				imageNames.put("gene0", "data:image/png;base64," + encodedImages.geneReversed)
				imageNames.put("gene1", "data:image/png;base64," + encodedImages.gene)
				imageNames.put("cloningsite0", "data:image/png;base64," + encodedImages.cloningsiteReversed)
				imageNames.put("cloningsite1", "data:image/png;base64," + encodedImages.cloningsite)
				imageNames.put("terminator0", "data:image/png;base64," + encodedImages.terminatorReversed)
				imageNames.put("terminator1", "data:image/png;base64," + encodedImages.terminator)

				var List<String> source = newArrayList
				source.add("<HTML>")
				source.add("<BODY BGCOLOR='#FCFCF0' STYLE='font-size:12px'>")

				// val path = pathToImages + File.separator
				for (cell : biocompilerModel.cells) {
					source.add("<H2>Cell: " + cell.name + "</H2>")
					var col = -1
					var deviceLength = 0
					var template = '''
						<TABLE cellpadding=0px cellspacing=0px >
						<TR align=center>
						«FOR device : cell.devices.sortBy[parts.get(0).position.value]»
							«{
					col = (col + 1) % colours.size;
					''
				}»
							«{
					deviceLength = device.parts.size;
					''
				}»
							<TD COLSPAN = «deviceLength» bgcolor='«colours.get(col)»'>
							«device.name»
							</TD>
						«ENDFOR»
						</TR>
						«{
					col = -1;
					''
				}»
						
						<TR>
						«FOR device : cell.devices.sortBy[parts.get(0).position.value]»
							«{
					col = (col + 1) % colours.size;
					''
				}»
							«FOR part : device.parts.sortBy[position.value]»
								<TD BGCOLOR = '«colours.get(col)»'><IMG TITLE ='«part.name»: «utils.sequenceToolTip(part.sequence)» ' SRC='«imageNames.
					get(part.biologicalFunction.toLowerCase + device.direction.value)»' BORDER=0 width=30px></TD>
							«ENDFOR»
						«ENDFOR»
						</TR>
						
						«{
					col = -1;
					''
				}»
						<TR align=center>
						«FOR device : cell.devices.sortBy[parts.get(0).position.value]»
							«{
					col = (col + 1) % colours.size;
					''
				}»
							«{
					deviceLength = device.parts.size;
					''
				}»
							<TD COLSPAN = «deviceLength» bgcolor='«colours.get(col)»'>
							&nbsp;
							</TD>
						«ENDFOR»
						</TR>
						
						</TABLE>
						
						«FOR device : cell.devices.sortBy[parts.get(0).position.value]»
							<h3>«device.name»</h3>
							<UL>
							«FOR part : device.parts.sortBy[position.value]»
								<LI>
								<b>«part.name»</b> («part.biologicalFunction») = «part.sequence»
								</LI>
							«ENDFOR»
							</UL>
						«ENDFOR»
					'''
					source.add(template)
				}

				source.add("</BODY>")
				source.add('</HTML>')
				return source.join('\n')

			}

			def String makeHtmlLog() {
				return log.toHtml
			}

			// for tests only
			def fillUpWithRandomSequences() {
				biocompilerModel.cells.forEach[devices.forEach[parts.forEach[sequence = utils.randomDNA(15)]]]
			}

		}
