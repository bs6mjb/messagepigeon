package com.messagepigeon;

import java.awt.*;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * The read-only viewer under the message grid: the headers of the highlighted message, and its data as Text, XML,
 * JSON, a tree or hex. The view you choose is remembered as you move between messages; a message that cannot be shown
 * that way (for example, not XML) falls back to Text until you highlight one that can.
 */
final class MessageViewer extends JPanel {
    private enum View { TEXT, XML, JSON, TREE, HEX }

    /** Messages bigger than this are not parsed for XML/JSON, to keep the grid responsive. */
    private static final int MAX_PARSE_BYTES = 2_000_000;

    private final JTextArea header = new JTextArea();
    private final JTextArea text = new JTextArea();
    private final JTextPane pretty = new JTextPane();
    private final JTree tree = new JTree(new DefaultMutableTreeNode(""));
    private final JTextArea hex = new JTextArea();
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JRadioButton rText = new JRadioButton("Text", true);
    private final JRadioButton rXml = new JRadioButton("XML");
    private final JRadioButton rJson = new JRadioButton("JSON");
    private final JRadioButton rTree = new JRadioButton("Tree");
    private final JRadioButton rHex = new JRadioButton("Hex");

    private View preferred = View.TEXT;
    private MessageRecord current;
    private String currentText = "";
    private boolean xmlOk;
    private boolean jsonOk;

    MessageViewer() {
        super(new BorderLayout());
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        for (JTextComponent c : new JTextComponent[] {header, text, pretty, hex}) {
            c.setEditable(false);
            c.setFont(mono);
        }
        header.setRows(5);
        header.setBackground(UIManager.getColor("Panel.background"));
        header.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        cardPanel.add(new JScrollPane(text), View.TEXT.name());
        cardPanel.add(new JScrollPane(pretty), "PRETTY");
        cardPanel.add(new JScrollPane(tree), View.TREE.name());
        cardPanel.add(new JScrollPane(hex), View.HEX.name());

        ButtonGroup group = new ButtonGroup();
        JPanel views = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        views.add(new JLabel("View as:"));
        addView(views, rText, View.TEXT, group);
        addView(views, rXml, View.XML, group);
        addView(views, rJson, View.JSON, group);
        addView(views, rTree, View.TREE, group);
        addView(views, rHex, View.HEX, group);

        JScrollPane headerScroll = new JScrollPane(header);
        headerScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        headerScroll.setPreferredSize(new Dimension(100, 88));
        JPanel top = new JPanel(new BorderLayout());
        top.add(views, BorderLayout.NORTH);
        top.add(headerScroll, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);
        clear();
    }

    private void addView(JPanel bar, JRadioButton button, View view, ButtonGroup group) {
        group.add(button);
        bar.add(button);
        button.addActionListener(e -> {
            preferred = view;
            render();
        });
    }

    /** Nothing highlighted. */
    void clear() {
        showNote("");
    }

    /** A line of text in place of a message, such as "3 messages selected". */
    void showNote(String note) {
        current = null;
        currentText = "";
        xmlOk = false;
        jsonOk = false;
        header.setText(note);
        text.setText("");
        for (JRadioButton r : new JRadioButton[] {rText, rXml, rJson, rTree, rHex}) {
            r.setEnabled(false);
        }
        rText.setSelected(true);
        cards.show(cardPanel, View.TEXT.name());
    }

    /** Shows a message's headers and data. */
    void show(MessageRecord m) {
        current = m;
        currentText = m.text();
        header.setText(headerText(m));
        header.setCaretPosition(0);
        boolean small = m.data.length <= MAX_PARSE_BYTES;
        xmlOk = small && Formats.isXml(currentText);
        jsonOk = small && !xmlOk && Formats.isJson(currentText);
        rText.setEnabled(true);
        rHex.setEnabled(true);
        rXml.setEnabled(xmlOk);
        rJson.setEnabled(jsonOk);
        rTree.setEnabled(xmlOk || jsonOk);
        render();
    }

    private static String headerText(MessageRecord m) {
        StringBuilder sb = new StringBuilder();
        sb.append("MsgId: ").append(MessageRecord.hex(m.msgId)).append('\n');
        sb.append("CorrelId: ").append(MessageRecord.hex(m.correlId)).append('\n');
        sb.append("Format: ").append(m.format).append("   CCSID: ").append(m.ccsid)
                .append("   Encoding: ").append(m.encoding).append("   Type: ").append(m.msgType)
                .append("   Size: ").append(m.data.length).append(" bytes\n");
        sb.append("ReplyTo: ").append(m.replyToQueue).append(" @ ").append(m.replyToQueueManager).append('\n');
        sb.append("User: ").append(m.userId).append("   Application: ").append(m.putApplication);
        return sb.toString();
    }

    /** The view that will actually be shown: the one you chose, or Text if this message cannot be shown that way. */
    private View effective() {
        return switch (preferred) {
            case XML -> xmlOk ? View.XML : View.TEXT;
            case JSON -> jsonOk ? View.JSON : View.TEXT;
            case TREE -> xmlOk || jsonOk ? View.TREE : View.TEXT;
            default -> preferred;
        };
    }

    private void render() {
        if (current == null) {
            return;
        }
        View v = effective();
        switch (v) {
            case TEXT -> rText.setSelected(true);
            case XML -> rXml.setSelected(true);
            case JSON -> rJson.setSelected(true);
            case TREE -> rTree.setSelected(true);
            case HEX -> rHex.setSelected(true);
        }
        try {
            switch (v) {
                case XML -> {
                    ViewerKit.show(pretty, Formats.prettyXml(currentText), ViewerKit.XML_TOKENS);
                    cards.show(cardPanel, "PRETTY");
                }
                case JSON -> {
                    ViewerKit.show(pretty, Formats.prettyJson(currentText), ViewerKit.JSON_TOKENS);
                    cards.show(cardPanel, "PRETTY");
                }
                case TREE -> {
                    DefaultMutableTreeNode root = xmlOk ? Formats.xmlTree(currentText) : Formats.jsonTree(currentText);
                    tree.setModel(new DefaultTreeModel(root));
                    for (int i = 0; i < tree.getRowCount() && i < 500; i++) {
                        tree.expandRow(i);
                    }
                    cards.show(cardPanel, View.TREE.name());
                }
                case HEX -> {
                    hex.setText(current.hexDump());
                    hex.setCaretPosition(0);
                    cards.show(cardPanel, View.HEX.name());
                }
                default -> {
                    text.setText(currentText);
                    text.setCaretPosition(0);
                    cards.show(cardPanel, View.TEXT.name());
                }
            }
        } catch (Exception e) {
            pretty.setText("Cannot display this view: " + e.getMessage());
            cards.show(cardPanel, "PRETTY");
        }
    }
}
