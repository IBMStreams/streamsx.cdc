package com.ibm.streamsx.cdc;

import java.text.SimpleDateFormat;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Utility {

	// Convert timestamp to ISO timestamp with microseconds
	public static final SimpleDateFormat ISO_DATEFORMAT = new SimpleDateFormat(
			"yyyy-MM-dd' 'HH:mm:ss.SSS'000'");

	// Get root element of XML file
	public static Element getRootElement(Document document) {
		return document.getDocumentElement();
	}

	// Get child element by name
	public static Element getChildElement(Element element, String tagName) {
		return (Element) element.getElementsByTagName(tagName).item(0);
	}

	// Get child element value
	public static String getChildElementValue(Element element, String tagName) {
		Element childElement = getChildElement(element, tagName);
		return childElement.getTextContent();
	}

	// Get all XML sub-nodes for a specific node
	public static NodeList getXmlTags(Node node, String tagname) {
		return ((Element) node).getElementsByTagName(tagname);
	}

	// Get XML attribute value for a specific node
	public static String getXMLValue(Node node, String attributeName) {
		return ((Element) node).getAttribute(attributeName);
	}

	// Get XML attribute value for a specific node, return as boolean
	public static boolean getXMLValueAsBoolean(Node node, String attributeName) {
		return Boolean.parseBoolean(getXMLValue(node, attributeName));
	}

}
