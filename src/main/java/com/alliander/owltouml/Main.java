package com.alliander.owltouml;

import com.alliander.owltouml.converter.OwlToUmlConverter;
import com.alliander.owltouml.exporters.EnterpriseArchitectNativeExporter;
import org.apache.commons.cli.*;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;

public class Main {
    
    public static void main(String[] args) throws OWLOntologyCreationException, TransformerException, ParserConfigurationException {
        Options options = new Options();
        Option ontology = Option.builder("o")
                .longOpt("ontology")
                .hasArg()
                .argName("uri")
                .desc("Specify the uri of the ontology.")
                .build();
        Option prefix = Option.builder("p")
                .longOpt("prefix")
                .hasArg()
                .argName("prefix")
                .desc("Specify the prefix of the ontology.")
                .build();
        options.addOption(ontology);
        options.addOption(prefix);
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            CommandLine cmd = parser.parse( options, args);
            if (cmd.hasOption("ontology") && cmd.hasOption("prefix")) {

                OwlToUmlConverter converter = new OwlToUmlConverter();
                converter.loadOntology(cmd.getOptionValue("ontology"), cmd.getOptionValue("prefix"));
                EnterpriseArchitectNativeExporter exporter = new EnterpriseArchitectNativeExporter();
                exporter.setUmlModel(converter.convertToUml());
                exporter.export(cmd.getOptionValue("prefix"));
            } else {
                System.out.println("Please enter both the ontology uri and the prefix --ontology example-prefix --prefix example-prefix");
            }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("SchemaGenerator", options);
            System.exit(1);
        }
    }
}
