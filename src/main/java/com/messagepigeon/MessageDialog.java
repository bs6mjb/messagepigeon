package com.messagepigeon;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.Charset;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * Detail window for one message: all headers, and the data in several views (plain text, XML, JSON, tree, hex).
 * The text view is editable; saving puts the edited message back on the queue.
 */
final class MessageDialog extends JDialog {
    /** How the dialog writes changes back to the queue. Each method runs {@code done} only on success. */
    interface Saver {
        void replace(MessageRecord original, MessageRecord edited, Runnable done);

        void putNew(MessageRecord edited, Runnable done);
    }

    private final MessageRecord original;
    private final Saver saver;
    private final Charset charset;
    private final String originalText;
    private final boolean crlf;
    private final boolean editable;

    private final JTextArea editor = new JTextArea();
    private final JTextPane pretty = new JTextPane();
    private final JTree tree = new JTree(new DefaultMutableTreeNode("(nothing to show)"));
    private final JTextArea hex = new JTextArea();
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JRadioButton rText = new JRadioButton("Text", true);
    private final JRadioButton rXml = new JRadioButton("XML");
    private final JRadioButton rJson = new JRadioButton("JSON");
    private final JRadioButton rTree = new JRadioButton("Tree");
    private final JRadioButton rHex = new JRadioButton("Hex");
    private final JButton formatBtn = new JButton("Format text");
    private final JComboBox<String> persistence = new JComboBox<>(new String[] {"Persistent", "Non-persistent"});
    private final JSpinner priority = new JSpinner(new SpinnerNumberModel(0, 0, 9, 1));
    private final JTextField replyQueue = new JTextField(20);
    private final JTextField replyManager = new JTextField(20);
    private final javax.swing.Timer detectTimer;
    private boolean xmlOk;
    private boolean jsonOk;

    MessageDialog(Frame owner, String queue, String profileName, MessageRecord m, Saver saver) {
        super(owner, "Message - " + queue + " @ " + profileName, false);
        this.original = m;
        this.saver = saver;
        this.charset = MessageRecord.charsetFor(m.ccsid);
        this.originalText = m.text();
        this.crlf = originalText.contains("\r\n");
        this.editable = Arrays.equals(originalText.getBytes(charset), m.data);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });

        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        editor.setFont(mono);
        editor.setTabSize(2);
        editor.setText(originalText.replace("\r\n", "\n"));
        editor.setCaretPosition(0);
        editor.setEditable(editable);
        pretty.setFont(mono);
        pretty.setEditable(false);
        hex.setFont(mono);
        hex.setEditable(false);
        tree.setRootVisible(true);
        cardPanel.add(new JScrollPane(editor), "text");
        cardPanel.add(new JScrollPane(pretty), "pretty");
        cardPanel.add(new JScrollPane(tree), "tree");
        cardPanel.add(new JScrollPane(hex), "hex");

        persistence.setSelectedIndex(m.persistence == 1 ? 0 : 1);
        priority.setValue(m.priority);
        replyQueue.setText(m.replyToQueue);
        replyManager.setText(m.replyToQueueManager);

        ButtonGroup g = new ButtonGroup();
        JPanel views = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        views.add(new JLabel("View as:"));
        for (JRadioButton b : new JRadioButton[] {rText, rXml, rJson, rTree, rHex}) {
            g.add(b);
            views.add(b);
            b.addActionListener(e -> showView());
        }
        views.add(Box.createHorizontalStrut(20));
        views.add(formatBtn);
        formatBtn.setToolTipText("Re-indent the text in the editor (XML or JSON)");
        formatBtn.addActionListener(e -> formatText());
        if (!editable) {
            JLabel warn = new JLabel("Binary content: shown read-only (use Hex).");
            warn.setForeground(new Color(0xB00020));
            views.add(warn);
        }

        JButton save = new JButton("Save to Queue");
        JButton copy = new JButton("Put as New Message");
        JButton close = new JButton("Close");
        save.setToolTipText("Replaces the original, keeping its Message ID (it re-joins the queue behind others "
                + "of the same priority)");
        save.addActionListener(e -> save(true));
        copy.addActionListener(e -> save(false));
        close.addActionListener(e -> close());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(save);
        buttons.add(copy);
        buttons.add(close);

        JPanel centre = new JPanel(new BorderLayout());
        centre.add(views, BorderLayout.NORTH);
        centre.add(cardPanel, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(950, 720);
        setLocationRelativeTo(owner);

        detectTimer = new javax.swing.Timer(350, e -> detect());
        detectTimer.setRepeats(false);
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                detectTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                detectTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                detectTimer.restart();
            }
        });
        detect(); // enables the XML/JSON/Tree choices when they apply; the view always starts as Text
        showView();
    }

    // ---- header ---------------------------------------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        MessageRecord m = original;
        String put = m.putDateTime == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(m.putDateTime);
        field(p, 0, 0, "Message ID", readOnly(MessageRecord.hex(m.msgId), 34));
        field(p, 0, 1, "Correlation ID", readOnly(MessageRecord.hex(m.correlId), 34));
        field(p, 1, 0, "Put date/time", readOnly(put, 14));
        field(p, 1, 1, "Format", readOnly(m.format, 14));
        field(p, 2, 0, "CCSID / encoding", readOnly(m.ccsid + " / " + m.encoding, 14));
        field(p, 2, 1, "Size", readOnly(m.data.length + " bytes", 14));
        field(p, 3, 0, "Put by", readOnly(m.userId + "  " + m.putApplication, 14));
        field(p, 3, 1, "Message type", readOnly(Integer.toString(m.msgType), 14));
        JPanel pp = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pp.add(persistence);
        pp.add(new JLabel("Priority"));
        pp.add(priority);
        field(p, 4, 0, "Persistence", pp);
        field(p, 4, 1, "Reply-to queue", replyQueue);
        field(p, 5, 1, "Reply-to manager", replyManager);
        return p;
    }

    private static JTextField readOnly(String text, int cols) {
        JTextField f = new JTextField(text, cols);
        f.setEditable(false);
        f.setCaretPosition(0);
        return f;
    }

    private static void field(JPanel p, int row, int col, String label, JComponent c) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = row;
        g.insets = new Insets(2, 4, 2, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = col * 2;
        p.add(new JLabel(label), g);
        g.gridx = col * 2 + 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        p.add(c, g);
    }

    // ---- views ----------------------------------------------------------------------------------------------

    private void detect() {
        String t = editor.getText();
        xmlOk = Formats.isXml(t);
        jsonOk = !xmlOk && Formats.isJson(t);
        rXml.setEnabled(xmlOk);
        rJson.setEnabled(jsonOk);
        rTree.setEnabled(xmlOk || jsonOk);
        formatBtn.setEnabled(editable && (xmlOk || jsonOk));
        if ((rXml.isSelected() && !xmlOk) || (rJson.isSelected() && !jsonOk) || (rTree.isSelected() && !xmlOk && !jsonOk)) {
            rText.setSelected(true);
            showView();
        }
    }

    private void showView() {
        String t = editor.getText();
        try {
            if (rXml.isSelected()) {
                ViewerKit.show(pretty, Formats.prettyXml(t), ViewerKit.XML_TOKENS);
                cards.show(cardPanel, "pretty");
            } else if (rJson.isSelected()) {
                ViewerKit.show(pretty, Formats.prettyJson(t), ViewerKit.JSON_TOKENS);
                cards.show(cardPanel, "pretty");
            } else if (rTree.isSelected()) {
                DefaultMutableTreeNode root = xmlOk ? Formats.xmlTree(t) : Formats.jsonTree(t);
                tree.setModel(new DefaultTreeModel(root));
                for (int i = 0; i < tree.getRowCount() && i < 500; i++) {
                    tree.expandRow(i);
                }
                cards.show(cardPanel, "tree");
            } else if (rHex.isSelected()) {
                hex.setText(MessageRecord.hexDump(currentBytes()));
                hex.setCaretPosition(0);
                cards.show(cardPanel, "hex");
            } else {
                cards.show(cardPanel, "text");
            }
        } catch (Exception e) {
            pretty.setText("Cannot display this view: " + e.getMessage());
            cards.show(cardPanel, "pretty");
        }
    }

    private void formatText() {
        try {
            String t = editor.getText();
            editor.setText(xmlOk ? Formats.prettyXml(t) : Formats.prettyJson(t));
            editor.setCaretPosition(0);
            rText.setSelected(true);
            showView();
        } catch (Exception e) {
            Ui.error(this, "Could not format the text", e);
        }
    }

    // ---- saving ---------------------------------------------------------------------------------------------

    private byte[] currentBytes() {
        String t = editor.getText();
        if (t.equals(originalText.replace("\r\n", "\n"))) {
            return original.data; // untouched: keep the exact original bytes
        }
        return (crlf ? t.replace("\n", "\r\n") : t).getBytes(charset);
    }

    private boolean isModified() {
        return !Arrays.equals(currentBytes(), original.data)
                || (persistence.getSelectedIndex() == 0 ? 1 : 0) != original.persistence
                || (Integer) priority.getValue() != original.priority
                || !replyQueue.getText().trim().equals(original.replyToQueue)
                || !replyManager.getText().trim().equals(original.replyToQueueManager);
    }

    private MessageRecord edited() {
        MessageRecord r = original.copy();
        r.data = currentBytes();
        r.persistence = persistence.getSelectedIndex() == 0 ? 1 : 0;
        r.priority = (Integer) priority.getValue();
        r.replyToQueue = replyQueue.getText().trim();
        r.replyToQueueManager = replyManager.getText().trim();
        return r;
    }

    private void save(boolean replace) {
        String warning = replace
                ? "Save your changes to the queue?\n\nThe edited message keeps the original's Message ID. MQ cannot "
                        + "update a message in place, so it is removed and put back, which places it behind the "
                        + "other messages of the same priority."
                : "Put the edited message on the queue as a new message? The original is left in place.";
        if (!Ui.confirm(this, replace ? "Save to Queue" : "Put as New Message", warning)) {
            return;
        }
        Runnable done = () -> {
            dispose();
        };
        if (replace) {
            saver.replace(original, edited(), done);
        } else {
            saver.putNew(edited(), done);
        }
    }

    private void close() {
        if (isModified() && !Ui.confirm(this, "Discard Changes", "Discard your changes to this message?")) {
            return;
        }
        dispose();
    }
}
