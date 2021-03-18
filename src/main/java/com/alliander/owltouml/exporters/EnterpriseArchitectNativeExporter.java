package com.alliander.owltouml.exporters;

import org.eclipse.uml2.uml.*;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Package;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.sql.SQLOutput;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EnterpriseArchitectNativeExporter {

    private Document dom;
    private Element root;
    private Element tPackage;
    private Element tObject;
    private Element tAttribute;
    private Element tDiagram;
    private Element tDiagramObjects;
    private Element currentPackage;
    private String inputPath;
    private int id;
    private HashMap<String, Element> elementList;
    private HashMap<String, List<String>> tree;
    private HashMap<String, List<String>> diagramList;
    private Element tConnector;
    private Model model;

    public void setUmlModel(Model model) {
        this.model = model;
    }

    public void export(String name) throws ParserConfigurationException, TransformerException {
        System.out.println("Start exporting.");
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        elementList = new HashMap<String, Element>();
        tree = new HashMap<String, List<String>>();
        diagramList = new HashMap<String, List<String>>();
        this.id = 0;
        this.dom = builder.newDocument();
        this.inputPath = "root";

        processModel();

        dom.appendChild(this.root);
        Transformer tr = TransformerFactory.newInstance().newTransformer();
        tr.setOutputProperty(OutputKeys.INDENT, "yes");
        tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        tr.setOutputProperty(OutputKeys.INDENT, "yes");
        tr.transform(new DOMSource(dom), new StreamResult(new File(name + ".xml")));
        System.out.println("Finished exporting.");
    }

    private void processModel() {
        Package rootPackage = this.model.getNestedPackages().get(0);
        createRootPackage(rootPackage.getName());
        rootPackage.getNestedPackages().forEach(p -> {
            createPackage(p.getName(), this.inputPath);
            p.getOwnedElements().forEach(e -> {
                if (e instanceof Class) {
                    Class cls = (Class) e;
                    String classGuid = getGuid();
                    String note = "";
                    for (Comment comment : cls.getOwnedComments()) {
                        note += comment.getBody() + "\n\n";
                    }
                    Element classObject = getTClassObject(cls.getName(), classGuid, getAttributeValue(this.elementList.get(this.inputPath+p.getName()), "ea_guid"), note);
                    this.tObject.appendChild(classObject);
                    this.elementList.put(cls.getName(), classObject);
                    this.tree.put(cls.getName(), new ArrayList<String>());

                    cls.getAttributes().forEach(attr -> {
                        if (attr.getAssociation() == null) {
                            String attrNote = "";
                            for (Comment comment : attr.getOwnedComments()) {
                                attrNote += comment.getBody() + "\n\n";
                            }
                            this.tAttribute.appendChild(getTAttributeRow(attr.getName(), getGuid(), classGuid, attrNote, attr.getType().getName()));
                        }
                    });
                }
            });
        });

        rootPackage.getNestedPackages().forEach(p -> {
            p.getOwnedElements().forEach(e -> {
                if (e instanceof  Class) {
                    Class cls = (Class) e;
                    // inheritance
                    cls.getSuperClasses().forEach(superCls -> {
                        Element parent = this.elementList.get(superCls.getName());
                        this.tConnector.appendChild(getTConnectorRow("Generalization", getAttributeValue(this.elementList.get(cls.getName()), "ea_guid"), getAttributeValue(parent, "ea_guid"), "", getAttributeValue(this.elementList.get(cls.getName()), "Object_ID"), getAttributeValue(parent, "Object_ID")));
                    });
                }
                if (e instanceof Association) {
                    Association assoc = (Association) e;
                    this.tConnector.appendChild(getTConnectorRow("Association", getAttributeValue(this.elementList.get(assoc.getMemberEnds().get(1).getType().getName()), "ea_guid"), getAttributeValue(this.elementList.get(assoc.getMemberEnds().get(0).getType().getName()), "ea_guid"), assoc.getMemberEnds().get(0).getName(), getAttributeValue(this.elementList.get(assoc.getMemberEnds().get(1).getType().getName()), "Object_ID"), getAttributeValue(this.elementList.get(assoc.getMemberEnds().get(0).getType().getName()), "Object_ID")));
                }
            });
        });
    }

    private void createRootPackage(String name) {
        this.root = this.dom.createElement("Package");
        String guid = getGuid();
        this.root.setAttribute("name", name);
        this.root.setAttribute("guid", guid);
        setupTables();

        // root package
        this.currentPackage = getTPackageRow(name, guid, "0", false, "");
        this.tPackage.appendChild(this.currentPackage);
        this.tObject.appendChild(getTPackageObject(name, guid, getAttributeValue(this.currentPackage, "Package_ID"), true, "", getAttributeValue(this.currentPackage, "Package_ID")));

        this.elementList.put(this.inputPath, this.currentPackage);
    }

    private void createPackage(String name, String parentPath) {
        String guid = getGuid();
        this.currentPackage = getTPackageRow(name, guid, getAttributeValue(this.elementList.get(parentPath), "Package_ID"), true, getAttributeValue(this.elementList.get(parentPath), "ea_guid"));
        this.tPackage.appendChild(this.currentPackage);
        this.tObject.appendChild(getTPackageObject(name, guid, getAttributeValue(this.elementList.get(parentPath), "Package_ID"), false, getAttributeValue(this.elementList.get(parentPath), "ea_guid"), getAttributeValue(this.currentPackage, "Package_ID")));
        this.elementList.put(this.inputPath + name, this.currentPackage);
    }

    private void setupTables() {
        this.tPackage = getTable("t_package");
        this.root.appendChild(this.tPackage);
        this.tObject = getTable("t_object");
        this.root.appendChild(this.tObject);
        this.tAttribute = getTable("t_attribute");
        this.root.appendChild(this.tAttribute);
        this.tConnector = getTable("t_connector");
        this.root.appendChild(this.tConnector);
        this.tDiagram = getTable("t_diagram");
        this.root.appendChild(this.tDiagram);
        this.tDiagramObjects = getTable("t_diagramobjects");
        this.root.appendChild(tDiagramObjects);
    }

    private Element getTDiagram(String guid, String diagramId, String name, String parentGuid) {
        Element row = this.dom.createElement("Row");
        row.appendChild(getColumn("ea_guid", guid));
        row.appendChild(getColumn("AttPub", "TRUE"));
        row.appendChild(getColumn("Diagram_ID", diagramId));
        row.appendChild(getColumn("Name", name));
        row.appendChild(getColumn("Diagram_Type", "Logical"));
        Element extension = this.dom.createElement("Extension");
        extension.setAttribute("Package_ID", parentGuid);
        row.appendChild(extension);
        return row;
    }

    private Element getTDiagramObjectRow(String parentGuid, String diagramId, String objectGuid, String top, String left, String right, String bottom) {
        Element row = this.dom.createElement("Row");
        row.appendChild(getColumn("RectTop", top));
        row.appendChild(getColumn("RectLeft", left));
        row.appendChild(getColumn("RectRight", right));
        row.appendChild(getColumn("RectBottom", bottom));
        Element extension = this.dom.createElement("Extension");
        extension.setAttribute("Diagram_ID", parentGuid);
        extension.setAttribute("Object_ID", objectGuid);
        row.appendChild(extension);
        return row;
    }

    private Element getTConnectorRow(String type, String startGuid, String endGuid, String destRole, String startId, String endId) {
        Element row = this.dom.createElement("Row");
        row.appendChild(getColumn("Connector_ID", getId()));
        row.appendChild(getColumn("Connector_Type", type));
        row.appendChild(getColumn("SourceIsAggregate", "0"));
        row.appendChild(getColumn("SourceIsOrdered", "0"));
        row.appendChild(getColumn("DestIsAggregate", "0"));
        row.appendChild(getColumn("DestIsOrdered", "0"));
        row.appendChild(getColumn("Start_Object_ID", startId));
        row.appendChild(getColumn("End_Object_ID", endId));
        row.appendChild(getColumn("Start_Edge", "0"));
        row.appendChild(getColumn("End_Edge", "0"));
        row.appendChild(getColumn("PtStartX", "0"));
        row.appendChild(getColumn("PtStartY", "0"));
        row.appendChild(getColumn("PtEndX", "0"));
        row.appendChild(getColumn("PtEndY", "0"));
        row.appendChild(getColumn("SeqNo", "0"));
        row.appendChild(getColumn("HeadStyle", "0"));
        row.appendChild(getColumn("LineStyle", "0"));
        row.appendChild(getColumn("RouteStyle", "0"));
        row.appendChild(getColumn("IsBold", "0"));
        row.appendChild(getColumn("LineColor", "0"));
        row.appendChild(getColumn("DiagramID", "0"));
        row.appendChild(getColumn("SourceIsNavigable", "FALSE"));
        row.appendChild(getColumn("DestIsNavigable", "FALSE"));
        row.appendChild(getColumn("IsRoot", "FALSE"));
        row.appendChild(getColumn("IsLeaf", "FALSE"));
        row.appendChild(getColumn("IsSpec", "FALSE"));
        row.appendChild(getColumn("IsSignal", "FALSE"));
        row.appendChild(getColumn("IsStimulus", "FALSE"));
        row.appendChild(getColumn("Target2", "0"));
        if (!destRole.isEmpty()) {
            row.appendChild(getColumn("DestRole", destRole));
            row.appendChild(getColumn("Direction", "Source -> Destination"));
        }
        Element extension = this.dom.createElement("Extension");
        extension.setAttribute("Start_Object_ID", startGuid);
        extension.setAttribute("End_Object_ID", endGuid);
        row.appendChild(extension);
        return row;
    }

    private Element getTAttributeRow(String name, String guid, String parentGuid, String note, String type) {
        Element row = this.dom.createElement("Row");
        row.appendChild(getColumn("Object_ID", getId()));
        row.appendChild(getColumn("Name", name));
        row.appendChild(getColumn("Scope", "Public"));
        row.appendChild(getColumn("IsStatic", "0"));
        row.appendChild(getColumn("IsCollection", "0"));
        row.appendChild(getColumn("IsOrdered", "0"));
        row.appendChild(getColumn("AllowDuplicates", "0"));
        row.appendChild(getColumn("LowerBound", "0"));
        row.appendChild(getColumn("UpperBound", "1"));
        if (!note.isEmpty())
            row.appendChild(getColumn("Notes", note));
        row.appendChild(getColumn("Pos", "0"));
        row.appendChild(getColumn("Length", "0"));
        row.appendChild(getColumn("Precision", "0"));
        row.appendChild(getColumn("Scale", "0"));
        row.appendChild(getColumn("Const", "0"));
        Element extension = this.dom.createElement("Extension");
        row.appendChild(getColumn("Type", type));
        row.appendChild(getColumn("ea_guid", guid));
        extension.setAttribute("Object_ID", parentGuid);
        row.appendChild(extension);
        return row;
    }

    private Element getTPackageRow(String name, String guid, String parentId, boolean hasParent, String parentGuid) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        Element row = this.dom.createElement("Row");
        row.appendChild(getColumn("Package_ID", getId()));
        row.appendChild(getColumn("Name", name));
        row.appendChild(getColumn("Parent_ID", parentId));

        row.appendChild(getColumn("CreatedDate", dtf.format(now)));
        row.appendChild(getColumn("ModifiedDate", dtf.format(now)));

        row.appendChild(getColumn("ea_guid", guid));

        row.appendChild(getColumn("IsControlled", "FALSE"));
        row.appendChild(getColumn("LastLoadDate", dtf.format(now)));
        row.appendChild(getColumn("LastSaveDate", dtf.format(now)));
        row.appendChild(getColumn("Protected", "FALSE"));
        row.appendChild(getColumn("UseDTD", "FALSE"));
        row.appendChild(getColumn("LogXML", "FALSE"));
        row.appendChild(getColumn("TPos", "0"));
        row.appendChild(getColumn("BatchSave", "0"));
        row.appendChild(getColumn("BatchLoad", "0"));

        Element extension = this.dom.createElement("Extension");
        if (hasParent)
            extension.setAttribute("Parent_ID", parentGuid);
        row.appendChild(extension);
        return row;
    }

    private Element getTClassObject(String name, String guid, String parentGuid, String note) {
        Element classObject = getTObjectRow(name, guid, "Class");
        Element extension = this.dom.createElement("Extension");
        if (!note.isEmpty())
            classObject.appendChild(getColumn("note", note));
        extension.setAttribute("Package_ID", parentGuid);
        classObject.appendChild(extension);
        return classObject;
    }

    private Element getTPackageObject(String name, String guid, String packageId, boolean isRoot, String parentGuid, String pdata1) {
        Element packageObject = getTObjectRow(name, guid, "Package");
        packageObject.appendChild(getColumn("Package_ID", packageId));
        packageObject.appendChild(getColumn("PDATA1", pdata1));
        packageObject.appendChild(getColumn("Diagram_ID", "0"));
        packageObject.appendChild(getColumn("Author", "OwlToUml"));
        packageObject.appendChild(getColumn("Version", "1.0"));
        packageObject.appendChild(getColumn("Complexity", "1"));
        packageObject.appendChild(getColumn("Status", "proposed"));
        packageObject.appendChild(getColumn("Abstract", "0"));
        packageObject.appendChild(getColumn("GenType", "Java"));
        packageObject.appendChild(getColumn("Phase", "1.0"));
        packageObject.appendChild(getColumn("Scope", "Public"));
        Element extension = this.dom.createElement("Extension");
        if (!isRoot)
            extension.setAttribute("Package_ID", parentGuid);
        extension.setAttribute("PDATA1", guid);
        packageObject.appendChild(extension);
        return packageObject;
    }

    private Element getTObjectRow(String name, String guid, String type) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        Element row = this.dom.createElement("Row");
        row.appendChild(getColumn("name", name));
        row.appendChild(getColumn("ea_guid", guid));
        row.appendChild(getColumn("Object_ID", getId()));
        row.appendChild(getColumn("Object_type", type));
        row.appendChild(getColumn("Classifier", "0"));
        row.appendChild(getColumn("ParentID", "0"));
        row.appendChild(getColumn("IsRoot", "FALSE"));
        row.appendChild(getColumn("IsLeaf", "FALSE"));
        row.appendChild(getColumn("IsSpec", "FALSE"));
        row.appendChild(getColumn("IsActive", "FALSE"));
        row.appendChild(getColumn("Tagged", "0"));
        row.appendChild(getColumn("TPos", "0"));
        row.appendChild(getColumn("Effort", "0"));
        row.appendChild(getColumn("Backcolor", "-1"));
        row.appendChild(getColumn("BorderStyle", "0"));
        row.appendChild(getColumn("BorderWidth", "-1"));
        row.appendChild(getColumn("Fontcolor", "-1"));
        row.appendChild(getColumn("Bordercolor", "-1"));
        row.appendChild(getColumn("CreatedDate", dtf.format(now)));
        row.appendChild(getColumn("ModifiedDate", dtf.format(now)));
        row.appendChild(getColumn("NType", "0"));
        return row;
    }

    private Element getColumn(String name, String value) {
        Element column = this.dom.createElement("Column");
        column.setAttribute("name", name);
        column.setAttribute("value", value);
        return column;
    }

    private Element getTable(String name) {
        Element table = this.dom.createElement("Table");
        table.setAttribute("name", name);
        return table;
    }

    private String getAttributeValue(Element element, String name) {
        for (Node node : iterable(element.getChildNodes())) {
            if (node.getAttributes().getNamedItem("name").getNodeValue().equals(name))
                return node.getAttributes().getNamedItem("value").getNodeValue();
        }
        return "";
    }

    private String getGuid() {
        return "{" + UUID.randomUUID() + "}";
    }

    private String getId() {
        this.id++;
        return "" + this.id;
    }

    public static Iterable<Node> iterable(final NodeList nodeList) {
        return () -> new Iterator<Node>() {
            private int index = 0;
            @Override
            public boolean hasNext() {
                return index < nodeList.getLength();
            }
            @Override
            public Node next() {
                if (!hasNext())
                    throw new NoSuchElementException();
                return nodeList.item(index++);
            }
        };
    }

}
