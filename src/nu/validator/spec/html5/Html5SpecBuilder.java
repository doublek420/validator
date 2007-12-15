/*
 * Copyright (c) 2007 Mozilla Foundation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL 
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */

package nu.validator.spec.html5;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.validator.htmlparser.common.XmlViolationPolicy;
import nu.validator.htmlparser.sax.HtmlParser;
import nu.validator.saxtree.DocumentFragment;
import nu.validator.saxtree.TreeBuilder;
import nu.validator.spec.Spec;
import nu.validator.xml.AttributesImpl;
import nu.validator.xml.EmptyAttributes;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import com.thaiopensource.xml.util.Name;

public class Html5SpecBuilder implements ContentHandler {

    private static final String NS = "http://www.w3.org/1999/xhtml";
    
    private static final String SPEC_LINK_URI = System.getProperty("nu.validator.spec.html5-link", "http://www.whatwg.org/specs/web-apps/current-work/");
    
    private static final String SPEC_LOAD_URI = System.getProperty("nu.validator.spec.html5-load", SPEC_LINK_URI);
    
    private static final Pattern ELEMENT = Pattern.compile("^.*element\\s*$");
    
    private static final Pattern CONTEXT = Pattern.compile("^\\s*Contexts\\s+in\\s+which\\s+this\\s+element\\s+may\\s+be\\s+used:\\s*");

    private static final Pattern CONTENT_MODEL = Pattern.compile("^\\s*Content\\s+model:\\s*$");

    private static final Pattern ATTRIBUTES = Pattern.compile("^\\s*Element-specific\\s+attributes:\\s*$");
    
    private enum State {
        AWAITING_HEADING,
        IN_H4,
        IN_CODE_IN_H4,
        AWAITING_ELEMENT_DL,
        IN_ELEMENT_DL_START,
        IN_CONTEXT_DT,
        CAPTURING_CONTEXT_DDS,
        IN_CONTENT_MODEL_DT,
        CAPTURING_CONTENT_MODEL_DDS,
        IN_ATTRIBUTES_DT,
        CAPTURING_ATTRIBUTES_DDS
    }
    
    private State state = State.AWAITING_HEADING;
    
    private int captureDepth = 0;
    
    private String currentId;
    
    private StringBuilder nameText = new StringBuilder();
    
    private StringBuilder referenceText = new StringBuilder();
    
    private TreeBuilder fragmentBuilder;
    
    private Name currentName;

    private Map<Name, String> urisByElement = new HashMap<Name, String>();

    private Map<Name, DocumentFragment> contextsByElement = new HashMap<Name, DocumentFragment>();

    private Map<Name, DocumentFragment> contentModelsByElement = new HashMap<Name, DocumentFragment>();

    private Map<Name, DocumentFragment> attributesByElement = new HashMap<Name, DocumentFragment>();
    
    public static Spec parseSpec() throws IOException, SAXException {
        HtmlParser parser = new HtmlParser(XmlViolationPolicy.ALTER_INFOSET);
        Html5SpecBuilder handler = new Html5SpecBuilder();
        parser.setContentHandler(handler);
        parser.parse(new InputSource(SPEC_LOAD_URI));
        return handler.buildSpec();
    }
    
    public static void main(String[] args) throws IOException, SAXException {
        parseSpec();
    }
    
    private Spec buildSpec() {
        return new Spec(urisByElement, contextsByElement, contentModelsByElement, attributesByElement);
    }

    /**
     * 
     */
    private Html5SpecBuilder() {
        super();
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        switch(state) {
            case AWAITING_HEADING:
                break;
            case IN_H4:
                referenceText.append(ch, start, length);
                break;
            case IN_CODE_IN_H4:
                nameText.append(ch, start, length);
                break;
            case AWAITING_ELEMENT_DL:
                break;
            case IN_ELEMENT_DL_START:
                break;
            case IN_CONTEXT_DT:
            case IN_CONTENT_MODEL_DT:
            case IN_ATTRIBUTES_DT:
                referenceText.append(ch, start, length);
                break;
            case CAPTURING_CONTEXT_DDS:
            case CAPTURING_CONTENT_MODEL_DDS:
            case CAPTURING_ATTRIBUTES_DDS:
                fragmentBuilder.characters(ch, start, length);
                break;
        }
    }

    public void endDocument() throws SAXException {
        switch(state) {
            case AWAITING_HEADING:
                // XXX finish
                break;
            case IN_H4:
            case IN_CODE_IN_H4:
            case AWAITING_ELEMENT_DL:
            case IN_ELEMENT_DL_START:
            case IN_CONTEXT_DT:
            case IN_CONTENT_MODEL_DT:
            case IN_ATTRIBUTES_DT:
            case CAPTURING_CONTEXT_DDS:
            case CAPTURING_CONTENT_MODEL_DDS:
            case CAPTURING_ATTRIBUTES_DDS:
                throw new SAXException(
                "Malformed spec: Wrong state for document end.");
        }
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        switch(state) {
            case AWAITING_HEADING:
                break;
            case IN_H4:
                if ("h4" == localName && NS == uri) {
                    Matcher m = ELEMENT.matcher(referenceText);
                    if (m.matches()) {
                        String ln = nameText.toString().intern();
                        if ("" == ln) {
                            throw new SAXException(
                                    "Malformed spec: no element currentName.");
                        }
                        if (currentId == null) {
                            throw new SAXException(
                                    "Malformed spec: no element id.");
                        }
                        currentName = new Name(NS, ln);
                        urisByElement.put(currentName, SPEC_LOAD_URI + "#" + currentId);
                        state = State.AWAITING_ELEMENT_DL;
                    } else {
                        currentId = null;
                        nameText.setLength(0);
                        state = State.AWAITING_HEADING;
                    }
                }
                break;
            case IN_CODE_IN_H4:
                if ("code" == localName && NS == uri) {
                    state = State.IN_H4;
                }
                break;
            case AWAITING_ELEMENT_DL:
                break;
            case IN_ELEMENT_DL_START:
                throw new SAXException(
                        "Malformed spec: no children in element dl.");
            case IN_CONTEXT_DT:
                if ("dt" == localName && NS == uri) {
                    Matcher m = CONTEXT.matcher(referenceText);
                    if (m.matches()) {
                        state = State.CAPTURING_CONTEXT_DDS;
                        captureDepth = 0;
                        fragmentBuilder = new TreeBuilder(true, true);
                    } else {
                        throw new SAXException(
                        "Malformed spec: Expected dt to be context dt but it was not.");                        
                    }
                }
                break;
            case IN_CONTENT_MODEL_DT:
                if ("dt" == localName && NS == uri) {
                    Matcher m = CONTENT_MODEL.matcher(referenceText);
                    if (m.matches()) {
                        state = State.CAPTURING_CONTENT_MODEL_DDS;
                        captureDepth = 0;
                        fragmentBuilder = new TreeBuilder(true, true);
                    } else {
                        throw new SAXException(
                        "Malformed spec: Expected dt to be context dt but it was not.");                        
                    }
                }
                break;
            case IN_ATTRIBUTES_DT:
                if ("dt" == localName && NS == uri) {
                    Matcher m = ATTRIBUTES.matcher(referenceText);
                    if (m.matches()) {
                        state = State.CAPTURING_ATTRIBUTES_DDS;
                        captureDepth = 0;
                        fragmentBuilder = new TreeBuilder(true, true);
                    } else {
                        throw new SAXException(
                        "Malformed spec: Expected dt to be context dt but it was not.");                        
                    }
                }
                break;
            case CAPTURING_CONTEXT_DDS:
            case CAPTURING_CONTENT_MODEL_DDS:
            case CAPTURING_ATTRIBUTES_DDS:
                if (captureDepth == 0) {
                    throw new SAXException(
                            "Malformed spec: Did not see following dt when capturing dds.");                    
                }
                captureDepth--;
                fragmentBuilder.endElement(uri, localName, qName);
                break;
        }
    }

    public void endPrefixMapping(String prefix) throws SAXException {
    }

    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
    }

    public void processingInstruction(String target, String data) throws SAXException {
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void skippedEntity(String name) throws SAXException {
    }

    public void startDocument() throws SAXException {
        // TODO Auto-generated method stub
        
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        switch(state) {
            case AWAITING_HEADING:
                if ("h4" == localName && NS == uri) {
                    referenceText.setLength(0);
                    currentId = null;
                    state = State.IN_H4;
                }
                break;
            case IN_H4:
                if ("code" == localName && NS == uri) {
                    nameText.setLength(0);
                    state = State.IN_CODE_IN_H4;
                } else if ("dfn" == localName && NS == uri) {
                    currentId = atts.getValue("", "id");
                } 
                break;
            case IN_CODE_IN_H4:
                break;
            case AWAITING_ELEMENT_DL:
                if ("dl" == localName && NS == uri && "element".equals(atts.getValue("", "class"))) {
                    state = State.IN_ELEMENT_DL_START;
                }
                break;
            case IN_ELEMENT_DL_START:
                if ("dt" == localName && NS == uri) {
                    referenceText.setLength(0);
                    state = State.IN_CONTEXT_DT;
                } else {
                    throw new SAXException(
                    "Malformed spec: Expected dt in dl.");                    
                } 
                break;
            case IN_CONTEXT_DT:
            case IN_CONTENT_MODEL_DT:
            case IN_ATTRIBUTES_DT:
                throw new SAXException(
                        "Malformed spec: Not expecting children in dts.");                    
            case CAPTURING_CONTEXT_DDS:
            case CAPTURING_CONTENT_MODEL_DDS:
            case CAPTURING_ATTRIBUTES_DDS:
                if ("dt" == localName && NS == uri && captureDepth == 0) {
                    DocumentFragment fragment = (DocumentFragment) fragmentBuilder.getRoot();
                    fragmentBuilder = null;
                    referenceText.setLength(0);
                    if (state == State.CAPTURING_CONTEXT_DDS) {
                        contextsByElement.put(currentName, fragment);
                        state = State.IN_CONTENT_MODEL_DT;
                    } else if (state == State.CAPTURING_CONTENT_MODEL_DDS) {
                        contentModelsByElement.put(currentName, fragment);                        
                        state = State.IN_ATTRIBUTES_DT;
                    } else {
                        attributesByElement.put(currentName, fragment);
                        state = State.AWAITING_HEADING;
                    }
                } else {
                    captureDepth++;
                    String href = null;
                    if ("a" == localName && NS == uri && (href = atts.getValue("", "href")) != null) {
                        if (href.startsWith("#")) {
                            href = SPEC_LINK_URI + href;
                        }
                        AttributesImpl attributesImpl = new AttributesImpl();
                        attributesImpl.addAttribute("href", href);
                        fragmentBuilder.startElement(uri, localName, qName, attributesImpl);
                    } else {
                        fragmentBuilder.startElement(uri, localName, qName, EmptyAttributes.EMPTY_ATTRIBUTES);
                    }
                }
                break;
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }
    
}