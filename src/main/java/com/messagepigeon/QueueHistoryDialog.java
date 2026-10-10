package com.messagepigeon;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;

/**
 * Profile right-click > Queue History...: a grid of the queues remembered for one profile, where you can remove the
 * highlighted entries or clear them all. Changes take effect straight away.
 */
final class QueueHistoryDialog extends JDialog {
    private final ConnectionProfile profile;
    private final Runnable onChanged;
    private final HistoryModel model;
    private final JTable table;
    private final JButton removeBtn = new JButton("Remove Selected");
    private final JButton clearBtn = new JButton("Clear All...");

    QueueHistoryDialog(Frame owner, ConnectionProfile profile, Runnable onChanged) {
        super(owner, "Queue History - " + profile.name, true);
        this.profile = profile;
        this.onChanged = onChanged;
        this.model = new HistoryModel(profile.recent());
        setIconImages(AppIcon.images());

        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.setFillsViewportHeight(true);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        // The Delete key removes the highlighted rows, as people expect from a list.
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeRows");
        table.getActionMap().put("removeRows", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                removeSelected();
            }
        });

        removeBtn.setIcon(MenuIcons.of(MenuIcons.Glyph.TRASH));
        removeBtn.setDisabledIcon(MenuIcons.faded(MenuIcons.Glyph.TRASH));
        removeBtn.setToolTipText("Forget the highlighted queues (or press Delete)");
        removeBtn.addActionListener(e -> removeSelected());
        clearBtn.setIcon(MenuIcons.of(MenuIcons.Glyph.TRASH_ALL));
        clearBtn.setDisabledIcon(MenuIcons.faded(MenuIcons.Glyph.TRASH_ALL));
        clearBtn.setToolTipText("Forget every queue remembered for this profile");
        clearBtn.addActionListener(e -> clearAll());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        JLabel intro = new JLabel("<html>Queues remembered for <b>" + escape(profile.name) + "</b>, most recent first. "
                + "Highlight one or more and choose Remove Selected, or clear them all.<br>"
                + "<font color='#666666'>This only forgets names in " + Ui.TITLE
                + "; it never touches the queue manager.</font></html>");
        intro.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.add(removeBtn);
        buttons.add(clearBtn);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        right.add(close);
        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.WEST);
        south.add(right, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(intro, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12),
                scroll.getBorder()));
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(close);
        setSize(520, 380);
        setLocationRelativeTo(owner);
        updateButtons();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void updateButtons() {
        removeBtn.setEnabled(table.getSelectedRowCount() > 0);
        clearBtn.setEnabled(model.getRowCount() > 0);
    }

    private void removeSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (int r : rows) {
            names.add(model.names.get(r));
        }
        for (String n : names) {
            profile.forgetQueue(n);
        }
        model.set(profile.recent());
        onChanged.run();
        updateButtons();
    }

    private void clearAll() {
        if (model.getRowCount() == 0
                || !Ui.confirm(this, "Clear Queue History", "Forget all " + model.getRowCount() + " queue name(s) remembered for '"
                        + profile.name + "'?")) {
            return;
        }
        profile.clearQueueHistory();
        model.set(profile.recent());
        onChanged.run();
        updateButtons();
    }

    /** The grid: recency rank and queue name. */
    private static final class HistoryModel extends AbstractTableModel {
        private List<String> names;

        HistoryModel(List<String> names) {
            this.names = new ArrayList<>(names);
        }

        void set(List<String> fresh) {
            names = new ArrayList<>(fresh);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return names.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int c) {
            return c == 0 ? "#" : "Queue";
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int r, int c) {
            return c == 0 ? r + 1 : names.get(r);
        }
    }
}
