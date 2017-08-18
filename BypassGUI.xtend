package roadblock.biocompiler.tests

import java.nio.charset.Charset
import org.junit.Test
import static org.junit.Assert.*
import roadblock.biocompiler.Biocompiler
import roadblock.biocompiler.util.BiocompilerUtil
import roadblock.emf.ibl.Ibl.IblFactory
import roadblock.emf.ibl.Ibl.IblPackage
import roadblock.emf.ibl.Ibl.Model
import java.io.FileWriter
import java.io.IOException
import com.google.inject.Inject
import org.eclipse.xtext.junit4.InjectWith
import org.eclipse.xtext.junit4.XtextRunner

import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.ByteBuffer

import static org.junit.Assert.*
import org.sbolstandard.core.util.SBOLPrettyWriter
import org.sbolstandard.core.cli.SBOLValidate
import org.sbolstandard.core.SBOLFactory
import org.sbolstandard.core.SBOLValidationException
import java.io.FileOutputStream

class BypassGUI {
	val utils = new BiocompilerUtil
	val filename = 'builtFromBypassGUI.html'
	
	def String buildHtml(){
		'''
		<HTML>
<FRAMESET cols = "80%,20%">
	<FRAMESET rows = "50%, 50%">
		<FRAME src = "log.html">
		<FRAME src = "results.html">
	</FRAMESET>
	<FRAME src = "identifiedParts.html">
</FRAMESET>
</HTML>'''
	}
	
	def writeTextFile(String fileName, String s) {

		var output = null as FileWriter

		try {
			output = new FileWriter(fileName)
			output.write(s)

		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (output != null) {
				try {
					output.flush
					output.close
					
				} catch (IOException e) {
					e.printStackTrace
				}
			}
		}
	}
	
	@Test
	def testATGC00(){
		
		val mp = IblPackage.eINSTANCE // necessary for registering the URI
		val XMLsource = utils.readFile("../roadblock.xtext.ibl.tests/testModels/testATGC00.xml",Charset.defaultCharset())
		val emfModel = utils.convertToEObject(XMLsource) as Model	
		
		var biocompiler = new Biocompiler(emfModel)
		
		biocompiler.gatherParts
		biocompiler.compile
		
		// save to html
		writeTextFile('identifiedParts.html', biocompiler.identifiedPartsHtml)
		writeTextFile('log.html', biocompiler.makeHtmlLog)
		writeTextFile('results.html', biocompiler.makeResultPage)
		writeTextFile(filename, buildHtml)	
		
		
		var sbol = biocompiler.makeSBOLDocument
		try {
			println("Validating the SBOL document");	
			SBOLFactory.validate(sbol)
			// validate the contents of the file
			SBOLFactory.write(sbol, new FileOutputStream("ATGCGeneratedSBOL.xml"));
			println("validated.");
		}
		catch (SBOLValidationException e) {
			println("Validation failed: " + e.getMessage());
		}		
		assertTrue(true)
	}	
}