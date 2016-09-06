package com.ibm.streamsx.cdc;

import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Utility {

	/**
	 * ISO timestamp format
	 */
	public static final SimpleDateFormat ISO_DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS'000'");

	static Node getNode(NodeList nodes, String tagName) {
		for (int x = 0; x < nodes.getLength(); x++) {
			Node node = nodes.item(x);
			if (node.getNodeName().equalsIgnoreCase(tagName)) {
				return node;
			}
		}

		return null;
	}

	static String getNodeValue(NodeList nodes, String tagName) {
		for (int x = 0; x < nodes.getLength(); x++) {
			Node node = nodes.item(x);
			if (node.getNodeName().equalsIgnoreCase(tagName)) {
				NodeList childNodes = node.getChildNodes();
				for (int y = 0; y < childNodes.getLength(); y++) {
					Node data = childNodes.item(y);
					if (data.getNodeType() == Node.TEXT_NODE)
						return data.getNodeValue();
				}
			}
		}
		return "";
	}

	/**
	 * Get root element of XML file
	 * 
	 * @param document
	 *            XML document
	 * @return The root element of the XML document
	 */
	public static Element getRootElement(Document document) {
		return document.getDocumentElement();
	}

	/**
	 * Get the first child element of a given name a parent element.
	 * 
	 * @param parent
	 *            The parent element for which the child must be retrieved.
	 * @param tagName
	 *            The name of the child that must be retrieved.
	 * @return
	 */
	// Get first child element by name
	public static Element getFirstChildElement(Element parent, String tagName) {
		return (Element) parent.getElementsByTagName(tagName).item(0);
	}

	/**
	 * Get all child elements of a certain tag name.
	 * 
	 * @param parent
	 *            The element from which the children elements must be
	 *            retrieved.
	 * @param tagName
	 *            The tag name of the children elements that must be retrieved
	 * @return A list of children elements. If no elements of the specified tag
	 *         name are found, an empty list is returned.
	 */
	public static List<Element> getChildElementsByName(Element parent, String tagName) {
		List<Element> elementList = new LinkedList<Element>();
		NodeList children = parent.getChildNodes();
		for (int c = 0; c < children.getLength(); c++) {
			if (children.item(c).getNodeType() == Node.ELEMENT_NODE && children.item(c).getNodeName().equals(tagName)) {
				elementList.add((Element) children.item(c));
			}
		}
		return elementList;
	}

	/**
	 * Return the first child element for the given tag name
	 * 
	 * @param parent
	 *            The element from which the children elements must be
	 *            retrieved.
	 * @param tagName
	 *            The tag name of the children element that must be retrieved
	 * @return One child element. If no elements of the specified tag name are
	 *         found, null is returned.
	 */
	public static Element getFirstChildElementByName(Element parent, String tagName) {
		List<Element> elementList = getChildElementsByName(parent, tagName);
		if (!elementList.isEmpty())
			return elementList.get(0);
		else
			return null;
	}

	/**
	 * Get the value of an attribute that belongs to an XML element.
	 * 
	 * @param node
	 *            XML element
	 * @param attributeName
	 *            Name of the attribute
	 * @return
	 */
	public static String getXMLAttributeValue(Element node, String attributeName) {
		return node.getAttribute(attributeName);
	}

	/**
	 * Get the boolean value of an attribute that belongs to an XML element
	 * 
	 * @param node
	 *            XML element
	 * @param attributeName
	 *            Name of the boolean attribute
	 * @return
	 */
	public static boolean getXMLAttributeValueAsBoolean(Element node, String attributeName) {
		return Boolean.parseBoolean(getXMLAttributeValue(node, attributeName));
	}

}
