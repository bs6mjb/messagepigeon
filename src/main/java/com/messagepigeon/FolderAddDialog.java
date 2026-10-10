package com.messagepigeon;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Confirms adding the files of a folder to a queue as one message each. The user can limit it to certain file
 * extensions, choose the order (file name, or date modified either way), and sees straight away how many files match
 * and which would go first and last.
 */
final class FolderAddDialog extends JDialog {
    /** What the user decided: the files to add, already in order. */
    record Result(List<Path> files, MqService.FileOrder order, String filter) {
        /** A short description for the status line, such as "xml, json; date modified, oldest first". */
        String describe() {
            String types = filter.isBlank() ? "all file types" : String.join(", ", MqService.parseExtensions(filter));
            return types + "; " + order.label.toLowerCase(java.util.Locale.ROOT);
        }
    }

    static final long MAX_MESSAGE_BYTES = 100L * 1024 * 1024; // MQ's own limit for one message
    static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;   // keeps the whole batch comfortably in memory

    /** The choices last made, kept for the rest of the run. */
    private static MqService.FileOrder lastOrder = MqService.FileOrder.NAME;
    private static String lastFilter = "";

    private final List<MqService.FileInfo> all;
    private final JTextField filterField = new JTextField(28);
    private final JLabel summary = new JLabel(" ");
    private final JButton add = new JButton("Add Files");
    private MqService.FileOrder order = lastOrder;
    private List<MqService.FileInfo> matching = List.of();
    private Result chosen;

    /**
     * @param files   the non-empty files in the folder
     * @param emptied how many empty files were left out already (shown for information)
     * @return what to add, or null if the user cancelled
     */
    static Result ask(Component parent, Path dir, String queue, List<MqService.FileInfo> files, int emptied) {
        FolderAddDialog d = new FolderAddDialog(parent, dir, queue, files, emptied);
        d.setVisible(true);
        return d.chosen;
    }

    FolderAddDialog(Component parent, Path dir, String queue, List<MqService.FileInfo> files, int emptied) {
        super(parent == null ? null : SwingUtilities.getWindowAncestor(parent), "Add Files From Folder",
                ModalityType.APPLICATION_MODAL);
        this.all = files;
        setIconImages(AppIcon.images());

        StringBuilder head = new StringBuilder("<html><div style='width:460px'>Add files from<br><b>")
                .append(Ui.html(dir.toString())).append("</b><br>to <b>").append(Ui.html(queue))
                .append("</b> as one message per file.<br>Subfolders are not included");
        if (emptied > 0) {
            head.append("; ").append(emptied).append(" empty file(s) are skipped");
        }
        head.append(".</div></html>");

        filterField.setText(lastFilter);
        filterField.setToolTipText("Extensions to include, separated by commas or spaces, for example: xml, json, txt");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update();
            }
        });
        JPanel filterRow = new JPanel(new BorderLayout(8, 0));
        filterRow.add(new JLabel("Only these file types:"), BorderLayout.WEST);
        filterRow.add(filterField, BorderLayout.CENTER);
        JLabel filterHint = new JLabel("e.g.  xml, json, txt      (leave empty to add every file)");
        filterHint.setFont(filterHint.getFont().deriveFont(Font.PLAIN, 11f));
        filterHint.setForeground(Color.GRAY);
        JPanel filterBox = new JPanel(new BorderLayout(0, 2));
        filterBox.setBorder(BorderFactory.createTitledBorder("File types"));
        filterBox.add(filterRow, BorderLayout.NORTH);
        filterBox.add(filterHint, BorderLayout.CENTER);

        JPanel orderBox = new JPanel();
        orderBox.setLayout(new BoxLayout(orderBox, BoxLayout.Y_AXIS));
        orderBox.setBorder(BorderFactory.createTitledBorder("Add them in this order"));
        ButtonGroup group = new ButtonGroup();
        for (MqService.FileOrder o : MqService.FileOrder.values()) {
            JRadioButton r = new JRadioButton(o.label, o == order);
            r.addActionListener(e -> {
                order = o;
                update();
            });
            group.add(r);
            orderBox.add(r);
        }

        summary.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[] {new JLabel(head.toString()), filterBox, orderBox, summary}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            top.add(c);
            top.add(Box.createVerticalStrut(8));
        }
        top.setBorder(BorderFactory.createEmptyBorder(14, 16, 4, 16));

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        add.addActionListener(e -> {
            List<Path> files2 = MqService.sort(matching, order).stream().map(MqService.FileInfo::path).toList();
            lastOrder = order;
            lastFilter = filterField.getText();
            chosen = new Result(files2, order, filterField.getText());
            dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        buttons.add(add);
        buttons.add(cancel);

        setLayout(new BorderLayout());
        add(top, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(add);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        update();
        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                filterField.requestFocusInWindow(); // ready to type a filter straight away
            }
        });
    }

    private static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        return bytes < 1024 * 1024 ? String.format("%.1f KB", bytes / 1024.0)
                : String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String described(MqService.FileInfo f, MqService.FileOrder order) {
        String name = "<b>" + Ui.html(f.path().getFileName().toString()) + "</b>";
        if (order == MqService.FileOrder.NAME) {
            return name;
        }
        LocalDateTime t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(f.modified()), ZoneId.systemDefault());
        return name + " (modified " + t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ")";
    }

    /** Works out which files match the typed extensions and shows the count, size, and the first and last file. */
    private void update() {
        Set<String> extensions = MqService.parseExtensions(filterField.getText());
        matching = all.stream().filter(f -> MqService.matchesExtensions(f.path(), extensions)).toList();
        String red = "<font color='#B00020'>";
        if (matching.isEmpty()) {
            summary.setText("<html><div style='width:460px'>" + red + "No files match"
                    + (extensions.isEmpty() ? "" : " these file types") + ".</font></div></html>");
            add.setEnabled(false);
            add.setText("Add Files");
            return;
        }
        long total = matching.stream().mapToLong(MqService.FileInfo::size).sum();
        MqService.FileInfo big = matching.stream().filter(f -> f.size() > MAX_MESSAGE_BYTES).findFirst().orElse(null);
        List<MqService.FileInfo> ordered = MqService.sort(matching, order);
        StringBuilder sb = new StringBuilder("<html><div style='width:460px'><b>" + matching.size() + "</b> of "
                + all.size() + " files match (" + size(total) + " in total)<br>First: "
                + described(ordered.get(0), order) + "<br>Last: " + described(ordered.get(ordered.size() - 1), order));
        boolean ok = true;
        if (big != null) {
            sb.append("<br>").append(red).append(Ui.html(big.path().getFileName().toString()))
                    .append(" is larger than the 100 MB MQ allows for one message.</font>");
            ok = false;
        } else if (total > MAX_TOTAL_BYTES) {
            sb.append("<br>").append(red).append("These files total more than the 256 MB that can be added in one go. "
                    + "Narrow the file types, or add them in smaller groups.</font>");
            ok = false;
        }
        summary.setText(sb.append("</div></html>").toString());
        add.setEnabled(ok);
        add.setText(matching.size() == 1 ? "Add 1 File" : "Add " + matching.size() + " Files");
    }
}
