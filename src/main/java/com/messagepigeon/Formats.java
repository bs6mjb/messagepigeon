package com.messagepigeon;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** XML and JSON parsing, pretty-printing and tree building for the message viewer. */
final class Formats {
    private Formats() {}

    // ---- XML ------------------------------------------------------------------------------------------------

    static Document parseXml(String text) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // no XXE from queue contents
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(null); // errors are reported by the exception; the default handler also prints them
        return b.parse(new InputSource(new StringReader(text.trim())));
    }

    static boolean isXml(String text) {
        if (!text.trim().startsWith("<")) {
            return false;
        }
        try {
            parseXml(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String prettyXml(String text) throws Exception {
        Document d = parseXml(text);
        d.setXmlStandalone(true);
        stripBlankText(d);
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, text.trim().startsWith("<?xml") ? "no" : "yes");
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(d), new StreamResult(w));
        return w.toString().trim();
    }

    private static void stripBlankText(Node n) {
        NodeList kids = n.getChildNodes();
        for (int i = kids.getLength() - 1; i >= 0; i--) {
            Node k = kids.item(i);
            if (k.getNodeType() == Node.TEXT_NODE && k.getTextContent().isBlank()) {
                n.removeChild(k);
            } else {
                stripBlankText(k);
            }
        }
    }

    static DefaultMutableTreeNode xmlTree(String text) throws Exception {
        Document d = parseXml(text);
        stripBlankText(d);
        return xmlNode(d.getDocumentElement());
    }

    private static DefaultMutableTreeNode xmlNode(Node e) {
        StringBuilder label = new StringBuilder(e.getNodeName());
        NamedNodeMap attrs = e.getAttributes();
        if (attrs != null && attrs.getLength() > 0) {
            label.append("  [");
            for (int i = 0; i < attrs.getLength(); i++) {
                label.append(i > 0 ? ", " : "").append(attrs.item(i).getNodeName()).append("=\"")
                        .append(attrs.item(i).getNodeValue()).append('"');
            }
            label.append(']');
        }
        NodeList kids = e.getChildNodes();
        boolean onlyText = kids.getLength() > 0;
        for (int i = 0; i < kids.getLength(); i++) {
            short t = kids.item(i).getNodeType();
            if (t != Node.TEXT_NODE && t != Node.CDATA_SECTION_NODE) {
                onlyText = false;
            }
        }
        if (onlyText) {
            label.append(": ").append(kids.item(0).getTextContent().trim());
            return new DefaultMutableTreeNode(label.toString());
        }
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(label.toString());
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            switch (k.getNodeType()) {
                case Node.ELEMENT_NODE -> node.add(xmlNode(k));
                case Node.COMMENT_NODE -> node.add(new DefaultMutableTreeNode("<!-- " + k.getNodeValue().trim() + " -->"));
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE ->
                        node.add(new DefaultMutableTreeNode("#text: " + k.getTextContent().trim()));
                default -> { }
            }
        }
        return node;
    }

    // ---- JSON -----------------------------------------------------------------------------------------------

    /** A JSON number, kept as written so formatting never changes its value or precision. */
    record Num(String text) { }

    static Object parseJson(String text) {
        return new JsonParser(text.trim()).parseAll();
    }

    static boolean isJson(String text) {
        String t = text.trim();
        if (!t.startsWith("{") && !t.startsWith("[")) {
            return false;
        }
        try {
            parseJson(t);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static String prettyJson(String text) {
        StringBuilder sb = new StringBuilder();
        writeJson(parseJson(text), sb, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeJson(Object v, StringBuilder sb, int depth) {
        if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                indent(sb, depth + 1);
                sb.append(quote(e.getKey())).append(": ");
                writeJson(e.getValue(), sb, depth + 1);
                sb.append(++i < m.size() ? ",\n" : "\n");
            }
            indent(sb, depth);
            sb.append('}');
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < l.size(); i++) {
                indent(sb, depth + 1);
                writeJson(l.get(i), sb, depth + 1);
                sb.append(i + 1 < l.size() ? ",\n" : "\n");
            }
            indent(sb, depth);
            sb.append(']');
        } else {
            sb.append(scalar(v));
        }
    }

    private static void indent(StringBuilder sb, int depth) {
        sb.append("  ".repeat(depth));
    }

    private static String scalar(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String s) {
            return quote(s);
        }
        if (v instanceof Num n) {
            return n.text();
        }
        return v.toString();
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    static DefaultMutableTreeNode jsonTree(String text) {
        return jsonNode("JSON", parseJson(text));
    }

    @SuppressWarnings("unchecked")
    private static DefaultMutableTreeNode jsonNode(String label, Object v) {
        if (v instanceof Map<?, ?> m) {
            DefaultMutableTreeNode n = new DefaultMutableTreeNode(label + "  {" + m.size() + "}");
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                n.add(jsonNode(e.getKey(), e.getValue()));
            }
            return n;
        }
        if (v instanceof List<?> l) {
            DefaultMutableTreeNode n = new DefaultMutableTreeNode(label + "  [" + l.size() + "]");
            for (int i = 0; i < l.size(); i++) {
                n.add(jsonNode("[" + i + "]", l.get(i)));
            }
            return n;
        }
        return new DefaultMutableTreeNode(label + ": " + scalar(v));
    }

    /** Small recursive-descent parser; objects keep their key order. */
    private static final class JsonParser {
        private final String s;
        private int i;

        JsonParser(String s) {
            this.s = s;
        }

        Object parseAll() {
            Object v = value();
            ws();
            if (i != s.length()) {
                throw error("Unexpected text after the JSON value");
            }
            return v;
        }

        private RuntimeException error(String msg) {
            return new IllegalArgumentException(msg + " at position " + i);
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private Object value() {
            ws();
            if (i >= s.length()) {
                throw error("Unexpected end");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Object literal(String word, Object v) {
            if (!s.startsWith(word, i)) {
                throw error("Invalid literal");
            }
            i += word.length();
            return v;
        }

        private Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String t = s.substring(start, i);
            try {
                Double.parseDouble(t);
            } catch (NumberFormatException e) {
                i = start;
                throw error("Invalid value");
            }
            return new Num(t);
        }

        private String string() {
            i++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (i >= s.length()) {
                        break;
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw error("Bad unicode escape");
                            }
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> sb.append(e); // \" \\ \/
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (peek() == '}') {
                i++;
                return m;
            }
            while (true) {
                ws();
                if (peek() != '"') {
                    throw error("Expected a property name");
                }
                String k = string();
                ws();
                if (peek() != ':') {
                    throw error("Expected ':'");
                }
                i++;
                m.put(k, value());
                ws();
                char c = peek();
                i++;
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw error("Expected ',' or '}'");
                }
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (peek() == ']') {
                i++;
                return l;
            }
            while (true) {
                l.add(value());
                ws();
                char c = peek();
                i++;
                if (c == ']') {
                    return l;
                }
                if (c != ',') {
                    throw error("Expected ',' or ']'");
                }
            }
        }

        private char peek() {
            if (i >= s.length()) {
                throw error("Unexpected end");
            }
            return s.charAt(i);
        }
    }
}
