package com.alliander.owltouml.converter;

import org.eclipse.uml2.uml.*;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PrimitiveType;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Converter from OWL (OWL-API compatible) to UML (Eclipse UML2)
 */
public class OwlToUmlConverter {

    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private PrefixDocumentFormat prefixManager;
    private Model umlModel;
    private UMLFactory umlFactory;
    private String prefix;
    private HashMap<String, Package> packages;
    private Package rootPackage;
    private HashMap<IRI, Class> classes;
    private boolean verbose;

    public OwlToUmlConverter() {
        this.manager = OWLManager.createOWLOntologyManager();
        OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
        config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        this.manager.setOntologyLoaderConfiguration(config);
        this.umlFactory = UMLFactory.eINSTANCE;
    }

    public void loadOntology(String iri, String prefix) throws OWLOntologyCreationException {
        this.ontology = this.manager.loadOntology(IRI.create(iri));
        this.prefixManager = this.ontology.getFormat().asPrefixOWLDocumentFormat();
        this.packages = new HashMap<>();
        this.classes = new HashMap<>();
        this.prefix = prefix;
    }

    public Model convertToUml() {
        this.umlModel = this.umlFactory.createModel();
        this.rootPackage = this.umlModel.createNestedPackage(this.prefix + "-ontology");
        this.processPrefixes();
        this.processClasses();
        this.processDataProperties();
        this.processObjectProperties();
        this.processSubClassOf();
        return this.umlModel;
    }

    /**
     * Processes the prefixes, creates a package for each namespace
     */
    private void processPrefixes() {
        prefixManager.prefixNames().forEach(pre -> {
            String identifier = pre.replace(":", "");
            if (identifier.isEmpty()) identifier = this.prefix;
            if (!this.packages.containsKey(identifier)) {
                Package newPackage = this.packages.put(identifier, this.rootPackage.createNestedPackage(identifier));
                setAnnotations(this.prefixManager.getIRI(identifier), newPackage);
            }
        });
    }

    /**
     * Processes the classes
     */
    private void processClasses() {
        this.ontology.classesInSignature().forEach(cls -> {
            if (isValidPrefixIri(cls.getIRI())) {
                Class newCls = this.packages.get(getPrefix(cls.getIRI())).createOwnedClass(getIdentifier(cls.getIRI()), false);
                this.classes.put(cls.getIRI(), newCls);
                setAnnotations(cls.getIRI(), newCls);
            }
        });
        if (!this.classes.containsKey(IRI.create("http://www.w3.org/2002/07/owl#Thing"))) {
            Class thing = null;
            if (this.packages.containsKey("owl")) {
                thing = this.packages.get("owl").createOwnedClass("Thing", false);
            } else {
                Package newPackage = this.packages.put("owl", this.rootPackage.createNestedPackage("owl"));
                thing = newPackage.createOwnedClass("Thing", false);
            }
            this.classes.put(IRI.create("http://www.w3.org/2002/07/owl#Thing"), thing);
        }
    }

    /**
     * Processes data properties, turns them into UML attributes
     */
    private void processDataProperties() {
        this.ontology.dataPropertiesInSignature().forEach(dp -> {
            // get the range
            List<String> types = new ArrayList<>();
            types.add("xsd:string");
            this.ontology.dataPropertyRangeAxioms(dp).forEach(ax -> {
                types.add(ax.getRange().toString());
            });
            PrimitiveType type = this.umlFactory.createPrimitiveType();
            type.setName(types.get(types.size()-1));
            List<Class> classes = new ArrayList<>();
            this.ontology.dataPropertyDomainAxioms(dp).forEach(ax -> {
                ax.classesInSignature().forEach(cls -> {
                    if (iriIsTransformedToClass(cls.getIRI()))
                        classes.add(this.classes.get(cls.getIRI()));
                });
            });
            if (classes.size() == 0) classes.add(this.classes.get(IRI.create("http://www.w3.org/2002/07/owl#Thing")));
            classes.forEach(cls -> {
                if (isValidPrefixIri(dp.getIRI())) {
                    Property attr = cls.createOwnedAttribute(getPrefixedIdentifier(dp.getIRI()), type, 0, 1);
                    setAnnotations(dp.getIRI(), attr);
                }
            });
        });
    }

    /**
     * Processes object properties, turns them into uni-directional UML associations
     */
    private void processObjectProperties() {
        this.ontology.objectPropertiesInSignature().forEach(op -> {
            // get classes in range
            List<Class> classesInRange = new ArrayList<>();
            this.ontology.objectPropertyRangeAxioms(op).forEach(ax -> {
                ax.classesInSignature().forEach(cls -> {
                    if (iriIsTransformedToClass(cls.getIRI()))
                        classesInRange.add(this.classes.get(cls.getIRI()));
                });
            });
            // get classes in domain
            if (classesInRange.size() == 0) classesInRange.add(this.classes.get(IRI.create("http://www.w3.org/2002/07/owl#Thing")));
            List<Class> classesInDomain = new ArrayList<>();
            this.ontology.objectPropertyDomainAxioms(op).forEach(ax -> {
                ax.classesInSignature().forEach(cls -> {
                    if (iriIsTransformedToClass(cls.getIRI()))
                        classesInDomain.add(this.classes.get(cls.getIRI()));
                });
            });
            if (classesInDomain.size() == 0) classesInDomain.add(this.classes.get(IRI.create("http://www.w3.org/2002/07/owl#Thing")));
            classesInDomain.forEach(domainCls -> {
                classesInRange.forEach(rangeCls -> {
                    if (isValidPrefixIri(op.getIRI())) {
                        Association assoc = domainCls.createAssociation(true, AggregationKind.NONE_LITERAL, getPrefixedIdentifier(op.getIRI()), 0, 1, rangeCls, false, AggregationKind.NONE_LITERAL, "", 0, -1);
                        setAnnotations(op.getIRI(), assoc);
                    }
                });
            });
        });
    }

    /**
     * Processes subclassof axioms, turns them into UML inheritance
     */
    private void processSubClassOf() {
        this.ontology.classesInSignature().forEach(cls -> {
            if (this.classes.containsKey(cls.getIRI())) {
                this.ontology.subClassAxiomsForSubClass(cls).forEach(ax -> {
                    if (ax.getSubClass().isOWLClass() && ax.getSuperClass().isOWLClass()) {
                        IRI subClassIRI = ax.getSubClass().asOWLClass().getIRI();
                        IRI superClassIRI = ax.getSuperClass().asOWLClass().getIRI();
                        if (isValidPrefixIri(subClassIRI) && isValidPrefixIri(superClassIRI)) {
                            if (this.classes.containsKey(subClassIRI) && this.classes.containsKey(superClassIRI)) {
                                this.classes.get(subClassIRI).getSuperClasses().add(this.classes.get(superClassIRI));
                            } else {
                                this.log("Warning: ignoring subClassOf for " + subClassIRI + " -> " + superClassIRI);
                            }
                        }
                    }
                });
                if (this.ontology.subClassAxiomsForSubClass(cls).count() == 0) {
                    this.classes.get(cls.getIRI()).getSuperClasses().add(this.classes.get(IRI.create("http://www.w3.org/2002/07/owl#Thing")));
                }
            } else {
                this.log("Warning: ignoring subClassOf for " + cls.getIRI());
            }
        });
    }

    private void setAnnotations(IRI iri, Element el) {
        this.ontology.annotationAssertionAxioms(iri).forEach(an -> {
            el.createOwnedComment().setBody(an.getProperty() + " : " + an.annotationValue());
        });
    }

    /**
     * @return prefix given a prefix IRI
     */
    private String getPrefix(IRI iri) {
        String[] split = this.prefixManager.getPrefixIRIIgnoreQName(iri).split(":");
        if (!split[0].isEmpty()) {
            return split[0];
        } else {
            return this.prefix;
        }
    }

    private String getIdentifier(IRI iri) {
        return this.prefixManager.getPrefixIRIIgnoreQName(iri).split(":")[1];
    }

    private String getPrefixedIdentifier(IRI iri) {
        String prefixed = this.prefixManager.getPrefixIRIIgnoreQName(iri);
        String[] split = prefixed.split(":");
        if (!split[0].isEmpty()) {
            return prefixed;
        } else {
            return this.prefix + prefixed;
        }
    }

    private boolean isValidPrefixIri(IRI iri) {
        boolean isValid = this.prefixManager.getPrefixIRIIgnoreQName(iri) != null;
        if (!isValid) log("Warning: ignoring axiom for " + iri);
        return isValid;
    }

    private boolean iriIsTransformedToClass(IRI iri) {
        boolean isClass = this.classes.get(iri) != null;
        if (!isClass) log("Warning: ignoring axiom for " + iri);
        return isClass;
    }

    private void log(String message) {
        System.out.println(message);
    }
}