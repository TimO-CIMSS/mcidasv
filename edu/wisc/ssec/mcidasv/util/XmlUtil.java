/*
 * $Id$
 *
 * Copyright  1997-2004 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */






package edu.wisc.ssec.mcidasv.util;


import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;



/**
 * A collection of utilities for xml.
 *
 * @author IDV development team
 */

public abstract class XmlUtil extends ucar.unidata.xml.XmlUtil {

	/**
	 * Print all the attributes of the given node
	 * 
	 * @param parent
	 */
	public static void printNode(Node parent) {
		if (parent==null) {
			System.out.println("null node!");
			return;
		}
		System.out.println(parent.getNodeName() + " node:");
		NamedNodeMap attrs = parent.getAttributes();
		for(int i = 0 ; i<attrs.getLength() ; i++) {
			Attr attribute = (Attr)attrs.item(i);     
			System.out.println("  " + attribute.getName()+" = "+attribute.getValue());
		}
	}

    /**
     *  Find all of the  descendant elements of the given parent Node
     *  whose tag name.equals the given tag.
     *
     *  @param parent The root of the xml dom tree to search.
     *  @param tag The tag name to match.
     *  @return The list of descendants that match the given tag.
     */
    public static List<String> findDescendantNamesWithSeparator(Node parent, String tag, String separator) {
        ArrayList<String> found = new ArrayList<String>();
        findDescendantNamesWithSeparator(parent, tag, "", separator, found);
        return found;
    }
    
    /**
     *  Find all of the  descendant elements of the given parent Node
     *  whose tag name equals the given tag.
     *
     *  @param parent The root of the xml dom tree to search.
     *  @param tag The tag name to match.
     *  @param found The list of descendants that match the given tag.
     */
    private static void findDescendantNamesWithSeparator(Node parent, String tag, String descendants, String separator, List<String> found) {
            if (parent instanceof Element) {
            	String elementName = ((Element)parent).getAttribute("name");
            	if (!elementName.equals(""))
            		descendants += ((Element)parent).getAttribute("name");
                if (parent.getNodeName().equals(tag)) {
                found.add(descendants);
                }
            	if (!elementName.equals(""))
            		descendants += separator;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
        	findDescendantNamesWithSeparator(child, tag, descendants, separator, found);
        }
    }
    
    /**
     * Find the element described by nameList (path)
     * 
     * @param parent
     * @param nameList
     * @return
     */
    public static Element getElementAtNamedPath(Node parent, List<String>nameList) {
    	return getMakeElementAtNamedPath(parent, nameList, "", false);
    }
    
    /**
     * Make the element described by nameList (path)
     * 
     * @param parent
     * @param nameList
     * @return
     */
    public static Element makeElementAtNamedPath(Node parent, List<String>nameList, String tagname) {
    	return getMakeElementAtNamedPath(parent, nameList, tagname, true);
    }
    
    /**
     * Find the element described by nameList (path)
     * 
     * @param parent
     * @param nameList
     * @return
     */
    public static Element getMakeElementAtNamedPath(Node parent, List<String>nameList, String tagName, boolean makeNew) {
    	Element thisElement = null;
    	if (parent instanceof Element && nameList.size() > 0) {
    		for (int i=0; i<nameList.size(); i++) {
    			String thisName = nameList.get(i);
    			NodeList children = parent.getChildNodes();
    			boolean foundChild = false;
    			for (int j=0; j<children.getLength(); j++) {
    				Node child = children.item(j);
    				if (!(child instanceof Element)) continue;
    				if (XmlUtil.getAttribute(child, "name").equals(thisName)) {
    					if (i == nameList.size()-1) thisElement = (Element)child;
    					parent = child;
    					foundChild = true;
    					break;
    				}
    			}
    			
    			// Didn't find it where we expected to.  Create a new one.
    			if (makeNew && !foundChild && parent instanceof Element) {
    				try {
    					Element newElement = XmlUtil.create(tagName, (Element)parent);
    					newElement.setAttribute("name", thisName);
    					parent.appendChild(newElement);
        				parent = newElement;
        				thisElement = newElement;
    				}
    				catch (Exception ex) {
    					System.err.println("Error making new " + tagName + " node named " + thisName);
    					break;
    				}
    			}
    			
    		}
    	}
    	
    	return thisElement;
    }
    
}