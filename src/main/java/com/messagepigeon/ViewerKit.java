package com.messagepigeon;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/** Colouring for the XML and JSON views, shared by the message window and the viewer under the grid. */
final class ViewerKit {
    static final Pattern XML_TOKENS = Pattern.compile(
            "(?<comment><!--.*?-->)|(?<pi><\\?.*?\\?>)|(?<tag></?[\\w:.-]+|/?>)|(?<attr>[\\w:.-]+)(?==)"
                    + "|(?<val>\"[^\"]*\"|'[^']*')", Pattern.DOTALL);
    static final Pattern JSON_TOKENS = Pattern.compile(
            "(?<key>\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:))|(?<str>\"(?:\\\\.|[^\"\\\\])*\")"
                    + "|(?<num>-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)|(?<lit>\\btrue\\b|\\bfalse\\b|\\bnull\\b)");

    private static final Color BLUE = new Color(0x1F4E9E);
    private static final Color PURPLE = new Color(0x7A3E9D);
    private static final Color ORANGE = new Color(0xB5521B);
    private static final Color GREEN = new Color(0x1A7F37);
    private static final Color GRAY = new Color(0x6A737D);

    private ViewerKit() {}

    /** Puts the text in the pane and colours the tokens the pattern finds. */
    static void show(JTextPane pane, String text, Pattern tokens) {
        pane.setText(text);
        pane.setCaretPosition(0);
        Matcher m = tokens.matcher(text);
        while (m.find()) {
            Color c = null;
            if (has(m, "comment") || has(m, "pi")) {
                c = GRAY;
            } else if (has(m, "tag") || has(m, "key")) {
                c = BLUE;
            } else if (has(m, "attr") || has(m, "lit")) {
                c = PURPLE;
            } else if (has(m, "val") || has(m, "str")) {
                c = ORANGE;
            } else if (has(m, "num")) {
                c = GREEN;
            }
            if (c != null) {
                SimpleAttributeSet a = new SimpleAttributeSet();
                StyleConstants.setForeground(a, c);
                pane.getStyledDocument().setCharacterAttributes(m.start(), m.end() - m.start(), a, false);
            }
        }
    }

    private static boolean has(Matcher m, String group) {
        try {
            return m.group(group) != null;
        } catch (IllegalArgumentException e) {
            return false; // that group is not in this pattern
        }
    }
}
