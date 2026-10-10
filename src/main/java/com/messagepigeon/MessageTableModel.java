package com.messagepigeon;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** The grid of messages shown in a queue tab. */
final class MessageTableModel extends AbstractTableModel {
    private static final String[] COLS = {"#", "Put Date/Time", "Message ID", "Format", "Persist", "Prio", "Bytes",
        "Preview"};
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private List<MessageRecord> rows = new ArrayList<>();

    void set(List<MessageRecord> r) {
        rows = new ArrayList<>(r);
        fireTableDataChanged();
    }

    MessageRecord get(int i) {
        return rows.get(i);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLS.length;
    }

    @Override
    public String getColumnName(int c) {
        return COLS[c];
    }

    @Override
    public Class<?> getColumnClass(int c) {
        return c == 0 || c == 5 || c == 6 ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int r, int c) {
        MessageRecord m = rows.get(r);
        return switch (c) {
            case 0 -> r + 1;
            case 1 -> m.putDateTime == null ? "" : fmt.format(m.putDateTime);
            case 2 -> MessageRecord.hex(m.msgId);
            case 3 -> m.format;
            case 4 -> m.persistence == 1 ? "Yes" : "No";
            case 5 -> m.priority;
            case 6 -> m.data.length;
            default -> m.preview(150);
        };
    }
}
