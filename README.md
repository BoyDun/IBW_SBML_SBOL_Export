# SBML and SBOL Model Export from the Infobiotics Workbench
The Infobiotics Workbench (IBW) is a modeling tool that provides stochastic simulations that mimic the procession of complex biological pathways, verification of simulation rulesets, and biomatter compilation. More specifically, the process of biomatter compilation (biocompilation) includes computing cloning sites of best fit, locating ribosome-binding sites, and fetching DNA sequences. Its simulations are driven by designs written in the domain-specific Infobiotics Language (IBL). To enhance its compatibility with biological modeling standards, I present two functions that are able to parse an Eclipse Modeling Framework model built from IBL into either a SBOL or SBML document. The SBOL model accurately captures high-level interactions and sequence and structural information, while the SBML model maintains functional components including amounts and kinetic laws. A combination of the two comprehensively captures every aspect of an IBL model. The EMF model to be converted can also be preprocessed via biocompilation or flattening, both of which we account for during conversion. The logical step forward is to implement an import functionality that will allow for the generation of IBL models from external SBOL and SBML documents.
Supplementary code that I’ve written includes an export wizard for the user to interface with the conversion functionality and a function to fetch Synbiohub SBOL sequences from any relevant URIs provided in the IBL model to be compiled. I use the JSBML library for SBML conversion and updated the existing SBOL library to libSBOLj 2.0.
## Getting Started
ToDo
## Running the Tests
ToDo
## Built With
* [libSBOLj 2.0](https://github.com/SynBioDex/libSBOLj)
* [JSBML](https://github.com/sbmlteam/jsbml)
## Authors
* Peter Dun, bodun@stanford.edu
Feel free to reach out with any questions, comments, suggestions, bug reports, etc.
## License
This project is licensed under the MIT License - see the [LICENSE](https://github.com/BoyDun/IBW_SBML_SBOL_Export/blob/master/LICENSE)
## Acknowledgments
* Harold Fellermann and Chris Myers, my mentors who advised me through the entire GSoC process
* Laurentiu Mierla, for creating the export UI skeleton
