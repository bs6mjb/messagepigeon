package com.messagepigeon;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** One open queue: a grid of its messages plus the actions that work on it. */
final class QueueTab extends JPanel {
    private static final int MAX_DEFAULT = 1000;

    private interface Job<T> {
        T call() throws Exception;
    }

    private final Frame owner;
    private final Connection conn;
    private final String queue;
    private final MessageTableModel model = new MessageTableModel();
    private final JTable table = new JTable(model);
    private final JTextField maxField = new JTextField(Integer.toString(MAX_DEFAULT), 6);
    private final MessageViewer viewer = new MessageViewer();
    private final JLabel status = new JLabel(" ");
    private boolean busy;
    private final JMenuItem viewItem = iconItem("View / Edit Message...", MenuIcons.Glyph.VIEW,
            "View or edit the highlighted message");
    private final JMenuItem exportSelItem = iconItem("Export Selected to File...", MenuIcons.Glyph.EXPORT_SELECTED,
            "Export Selected to File - save only the highlighted messages to a file");
    private final JMenuItem deleteSelItem = iconItem("Delete Selected...", MenuIcons.Glyph.TRASH,
            "Permanently delete the highlighted messages from the queue");

    /** A menu item with its icon, and a faded one for when it is disabled. */
    private static JMenuItem iconItem(String text, MenuIcons.Glyph glyph, String tooltip) {
        JMenuItem i = new JMenuItem(text, MenuIcons.of(glyph));
        i.setDisabledIcon(MenuIcons.faded(glyph));
        i.setToolTipText(tooltip);
        return i;
    }

    QueueTab(Frame owner, Connection conn, String queue) {
        super(new BorderLayout());
        this.owner = owner;
        this.conn = conn;
        this.queue = queue;

        ((javax.swing.text.AbstractDocument) maxField.getDocument())
                .setDocumentFilter(new ProfileDialog.DigitsOnlyFilter(7));
        maxField.addActionListener(e -> browse(null)); // Enter re-reads the queue with the new limit
        maxField.setToolTipText("How many messages to read, oldest first. Press Enter or Refresh to apply.");

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bar.add(new JLabel("Max messages:"));
        bar.add(maxField);

        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetail();
            }
        });
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(230);
        table.getColumnModel().getColumn(7).setPreferredWidth(400);
        table.setToolTipText("Double-click a message to open it. Right-click for more.");
        installTableMenu();

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), viewer);
        split.setResizeWeight(0.7);
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        add(bar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    Connection connection() {
        return conn;
    }

    String queueName() {
        return queue;
    }

    // ---- table menu -----------------------------------------------------------------------------------------

    private void installTableMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem selectAll = new JMenuItem("Select All", MenuIcons.of(MenuIcons.Glyph.SELECT_ALL));
        viewItem.addActionListener(e -> openSelected());
        exportSelItem.addActionListener(e -> exportSelected());
        deleteSelItem.addActionListener(e -> deleteSelected());
        selectAll.addActionListener(e -> table.selectAll());
        menu.add(viewItem);
        menu.addSeparator();
        menu.add(exportSelItem);
        menu.add(deleteSelItem);
        menu.addSeparator();
        menu.add(selectAll);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        table.setRowSelectionInterval(row, row);
                        openSelected();
                    }
                }
            }

            private void popup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && !table.isRowSelected(row)) {
                    table.setRowSelectionInterval(row, row);
                }
                int n = table.getSelectedRowCount();
                viewItem.setEnabled(n == 1);
                exportSelItem.setEnabled(n > 0);
                exportSelItem.setText(n > 1 ? "Export " + n + " Selected to File..." : "Export Selected to File...");
                deleteSelItem.setEnabled(n > 0);
                deleteSelItem.setText(n > 1 ? "Delete " + n + " Selected..." : "Delete Selected...");
                menu.show(table, e.getX(), e.getY());
            }
        });
    }

    private List<MessageRecord> selected() {
        List<MessageRecord> sel = new ArrayList<>();
        for (int r : table.getSelectedRows()) {
            sel.add(model.get(table.convertRowIndexToModel(r)));
        }
        return sel;
    }

    // ---- actions --------------------------------------------------------------------------------------------

    private int max() {
        try {
            return Math.max(1, Integer.parseInt(maxField.getText().trim()));
        } catch (NumberFormatException e) {
            return MAX_DEFAULT;
        }
    }

    /** Reads the queue again; {@code note} (if any) leads the status line. */
    void browse(String note) {
        if (busyNow()) {
            return;
        }
        int max = max();
        setStatus("Reading " + queue + "...");
        run(() -> new Object[] {conn.mq.depth(queue), conn.mq.browse(queue, max)}, result -> {
            @SuppressWarnings("unchecked")
            List<MessageRecord> msgs = (List<MessageRecord>) ((Object[]) result)[1];
            int depth = (Integer) ((Object[]) result)[0];
            model.set(msgs);
            viewer.clear();
            setStatus((note == null ? "" : note + " - ") + queue + ": depth " + depth + ", showing " + msgs.size()
                    + (msgs.size() < depth ? " (raise Max messages to see more)" : ""));
        }, "Read failed");
    }

    private void openSelected() {
        if (table.getSelectedRowCount() != 1) {
            return;
        }
        MessageRecord m = selected().get(0);
        new MessageDialog(owner, queue, conn.profile.name, m, new MessageDialog.Saver() {
            @Override
            public void replace(MessageRecord original, MessageRecord edited, Runnable done) {
                run(() -> conn.mq.replace(queue, original.msgId, edited), ok -> {
                    if (ok) {
                        done.run();
                        browse("Message saved (same Message ID; now behind others of its priority)");
                    } else {
                        Ui.info(QueueTab.this, "Save Message to Queue", "The original message is no longer on the queue, so nothing was "
                                + "changed. Use \"Put as New Message\" if you still want to add your version.");
                        browse(null);
                    }
                }, "Save failed");
            }

            @Override
            public void putNew(MessageRecord edited, Runnable done) {
                run(() -> conn.mq.put(queue, List.of(edited)), n -> {
                    done.run();
                    browse("Added 1 message");
                }, "Put failed");
            }
        }).setVisible(true);
    }

    void insertMessage() {
        if (busyNow()) {
            return;
        }
        JTextArea text = new JTextArea(10, 60);
        JComboBox<String> persist = new JComboBox<>(new String[] {"Persistent", "Non-persistent"});
        JSpinner prio = new JSpinner(new SpinnerNumberModel(0, 0, 9, 1));
        JTextField replyQ = new JTextField(20);
        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT));
        opts.add(persist);
        opts.add(new JLabel("Priority"));
        opts.add(prio);
        opts.add(new JLabel("Reply-to queue"));
        opts.add(replyQ);
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.add(new JScrollPane(text), BorderLayout.CENTER);
        p.add(opts, BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(this, p, "Insert message into " + queue, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION || text.getText().isEmpty()) {
            return;
        }
        MessageRecord r = MqService.textMessage(text.getText());
        r.persistence = persist.getSelectedIndex() == 0 ? 1 : 0;
        r.priority = (Integer) prio.getValue();
        r.replyToQueue = replyQ.getText().trim();
        run(() -> conn.mq.put(queue, List.of(r)), n -> browse("Inserted 1 message"), "Insert failed");
    }

    /** The non-empty files found in a folder (before the user narrows them down), and how many empty ones were left out. */
    record FolderPlan(List<MqService.FileInfo> files, int empty) { }

    /** Adds one message per file in a folder (not its subfolders), optionally limited to some file extensions. */
    void addFromFolder() {
        if (busyNow()) {
            return;
        }
        java.nio.file.Path dir = Ui.chooseFolder(this, "Add Files From Folder - choose the folder");
        if (dir == null) {
            return;
        }
        run(() -> {
            List<MqService.FileInfo> files = new ArrayList<>();
            int empty = 0;
            for (MqService.FileInfo f : MqService.listFileInfo(dir)) {
                if (f.size() == 0) {
                    empty++;
                } else {
                    files.add(f);
                }
            }
            return new FolderPlan(files, empty);
        }, plan -> {
            if (plan.files().isEmpty()) {
                Ui.info(this, "Add Files From Folder", "There are no files to add in " + dir + "\n(subfolders, hidden "
                        + "files and empty files are skipped).");
                return;
            }
            FolderAddDialog.Result r = FolderAddDialog.ask(this, dir, queue, plan.files(), plan.empty());
            if (r == null) {
                return;
            }
            setStatus("Adding files from " + dir + "...");
            run(() -> {
                List<MessageRecord> recs = new ArrayList<>();
                for (java.nio.file.Path p : r.files()) {
                    recs.add(MqService.fileMessage(p));
                }
                return conn.mq.put(queue, recs);
            }, n -> browse("Added " + n + " messages from " + dir.getFileName() + " (" + r.describe() + ")"),
                    "Add from folder failed");
        }, "Could not read the folder");
    }

    void addFromFile() {
        if (busyNow()) {
            return;
        }
        String title = "Add Messages From File";
        Path f = Ui.choose(this, title + " - choose the text file", false);
        if (f == null) {
            return;
        }
        run(() -> {
            if (MqService.isBackupFile(f)) {
                throw new IllegalArgumentException("This is an export file; use Import from File instead.");
            }
            return MqService.readLines(f);
        }, recs -> {
            if (recs.isEmpty()) {
                Ui.info(this, title, "The file has no non-blank lines.");
                return;
            }
            if (!Ui.confirm(this, title, "Add " + recs.size() + " messages (one per line) to " + queue + "?")) {
                return;
            }
            run(() -> conn.mq.put(queue, recs), n -> browse("Added " + n + " messages"), "Add failed");
        }, "Could not read file");
    }

    void fullExport() {
        if (busyNow()) {
            return;
        }
        Path f = Ui.choose(this, "Export to File - save every message on " + queue, true);
        if (f == null) {
            return;
        }
        setStatus("Exporting " + queue + "...");
        run(() -> conn.mq.exportAll(queue, f), n -> setStatus("Exported all " + n + " messages to " + f),
                "Export failed");
    }

    private void exportSelected() {
        List<MessageRecord> sel = selected();
        if (sel.isEmpty()) {
            return;
        }
        Path f = Ui.choose(this, "Export Selected to File - save " + sel.size() + " message(s)", true);
        if (f == null) {
            return;
        }
        try {
            MqService.exportRecords(sel, f, conn.profile.queueManager + " / " + queue + " (selected messages)");
            setStatus("Exported " + sel.size() + " selected message(s) to " + f);
        } catch (Exception e) {
            Ui.error(this, "Export failed", e);
        }
    }

    void importFile() {
        if (busyNow()) {
            return;
        }
        String title = "Import from File";
        Path f = Ui.choose(this, title + " - choose an export file", false);
        if (f == null || !Ui.confirm(this, title, "Add all messages from " + f.getFileName() + " to " + queue + "?")) {
            return;
        }
        setStatus("Importing...");
        run(() -> conn.mq.importBackup(queue, f), n -> browse("Imported " + n + " messages"), "Import failed");
    }

    void deleteSelected() {
        if (busyNow()) {
            return;
        }
        String title = "Delete Selected Messages";
        List<MessageRecord> sel = selected();
        if (sel.isEmpty()) {
            Ui.info(this, title, "Select one or more messages in the grid first.");
            return;
        }
        if (!Ui.confirm(this, title, "Permanently delete " + sel.size() + " message(s) from " + queue + "?")) {
            return;
        }
        run(() -> {
            int removed = 0;
            for (MessageRecord m : sel) {
                if (conn.mq.delete(queue, m.msgId)) {
                    removed++;
                }
            }
            return removed;
        }, n -> browse("Deleted " + n + " message(s)"), "Delete failed");
    }

    void clearQueue() {
        if (busyNow()) {
            return;
        }
        if (!Ui.confirmTyped(this, "Delete All Messages",
                "This permanently deletes <b>ALL</b> messages on <b>" + Ui.html(queue) + "</b>",
                "Type the queue name to confirm:", queue)) {
            return;
        }
        setStatus("Clearing " + queue + "...");
        run(() -> conn.mq.clear(queue), n -> browse("Deleted " + n + " messages"), "Clear failed");
    }

    // ---- detail pane ----------------------------------------------------------------------------------------

    private void showDetail() {
        int n = table.getSelectedRowCount();
        if (n == 1) {
            viewer.show(selected().get(0));
        } else if (n > 1) {
            viewer.showNote(n + " messages selected");
        } else {
            viewer.clear();
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private void setStatus(String s) {
        status.setText(s);
    }

    /** Runs an MQ job on the connection's thread, then hands the result to the UI thread. */
    private <T> void run(Job<T> job, Consumer<T> onOk, String failStatus) {
        setBusy(true);
        try {
            conn.worker.execute(() -> {
                try {
                    T result = job.call();
                    SwingUtilities.invokeLater(() -> {
                        setBusy(false);
                        onOk.accept(result);
                    });
                } catch (Throwable t) {
                    SwingUtilities.invokeLater(() -> fail(failStatus, t));
                }
            });
        } catch (RuntimeException e) { // connection already closed
            fail(failStatus, e);
        }
    }

    private void fail(String failStatus, Throwable t) {
        setBusy(false);
        setStatus(failStatus);
        Ui.error(this, "Operation failed", t);
    }

    /**
     * True (after saying so in the status line) while an MQ operation is still running. The menu and icon bar stay
     * clickable, so each action checks this first rather than starting a second operation on top of the first.
     */
    private boolean busyNow() {
        if (busy) {
            setStatus("Please wait for the current operation to finish.");
        }
        return busy;
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        maxField.setEnabled(!busy);
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }
}
