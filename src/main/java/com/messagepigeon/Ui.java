package com.messagepigeon;

import com.ibm.mq.MQException;
import com.ibm.mq.constants.MQConstants;
import java.awt.Component;
import java.nio.file.Path;
import java.awt.BorderLayout;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;

/** Small dialog helpers shared by the windows. */
final class Ui {
    static final String TITLE = "MessagePigeon";

    private Ui() {}

    /**
     * The Windows look and feel keeps an empty 22-pixel column, a gap and a minimum text offset at the left of every
     * menu for check marks, which pushes the icons well in from the edge even though nothing here is checkable. This
     * removes them. Call it after the look and feel is set and before any menu is built.
     */
    static void tightenMenus() {
        if (!UIManager.getLookAndFeel().getClass().getName().contains("Windows")) {
            return;
        }
        Icon none = new Icon() {
            @Override
            public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
                // nothing to draw: this only stands in for the reserved check mark column
            }

            @Override
            public int getIconWidth() {
                return 0;
            }

            @Override
            public int getIconHeight() {
                return 0;
            }
        };
        for (String prefix : new String[] {"MenuItem", "Menu"}) {
            UIManager.put(prefix + ".checkIcon", none);
            UIManager.put(prefix + ".afterCheckIconGap", 4);
            UIManager.put(prefix + ".minimumTextOffset", 0);
        }
    }

    static void info(Component parent, String message) {
        info(parent, TITLE, message);
    }

    /** A message box whose title says what it is about, so it never shows a stock title such as "Message". */
    static void info(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    static boolean confirm(Component parent, String message) {
        return confirm(parent, TITLE, message);
    }

    /** An OK / Cancel question whose title says what is being decided. */
    static boolean confirm(Component parent, String title, String message) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    /**
     * Guards a destructive action: the user must type {@code expected} (for example the queue name). The box is wide
     * enough for the name and the text wraps instead of widening the window. Returns true only if they typed it.
     *
     * @param warningHtml what is about to happen, as HTML (escape any names with {@link #html})
     */
    static boolean confirmTyped(Component parent, String title, String warningHtml, String prompt, String expected) {
        JLabel text = new JLabel("<html><div style='width:440px'>" + warningHtml + "<br><br>" + html(prompt) + "</div></html>");
        JTextField field = new JTextField(Math.max(36, expected.length() + 8));
        field.addAncestorListener(new javax.swing.event.AncestorListener() {
            @Override
            public void ancestorAdded(javax.swing.event.AncestorEvent e) {
                field.requestFocusInWindow(); // ready to type as soon as the box opens
            }

            @Override
            public void ancestorRemoved(javax.swing.event.AncestorEvent e) {
            }

            @Override
            public void ancestorMoved(javax.swing.event.AncestorEvent e) {
            }
        });
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(text, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return false;
        }
        if (field.getText().trim().equals(expected)) {
            return true;
        }
        info(parent, title, "What you typed does not match, so nothing was changed.");
        return false;
    }

    /** Escapes text for use inside an HTML label. */
    static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static void error(Component parent, String what, Throwable t) {
        JOptionPane.showMessageDialog(parent, what + ":\n" + describe(t), TITLE, JOptionPane.ERROR_MESSAGE);
    }

    /** Explains an exception, naming the MQ reason code and the underlying cause when there is one. */
    static String describe(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            boolean failedToInitialise = c instanceof ExceptionInInitializerError
                    || (c instanceof NoClassDefFoundError && String.valueOf(c.getMessage()).startsWith("Could not initialize class"));
            if (failedToInitialise) {
                // The class exists but its start-up code failed: that is not a missing library, so say what failed.
                Throwable root = c;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                return "IBM's MQ client library failed to start up: " + c.getClass().getSimpleName() + " - "
                        + (root == c ? c.getMessage() : root)
                        + "\n\nThe IBM client library in use is:\n"
                        + System.getProperty("messagepigeon.mqjar", "(on the class path)")
                        + "\n\nIf this keeps happening, try a different copy of the library with "
                        + "Help > IBM MQ Client Library..., then restart.";
            }
            if (c instanceof NoClassDefFoundError || c instanceof ClassNotFoundException) {
                return "A library that IBM's MQ client needs is missing: " + c.getMessage()
                        + "\n\nThe IBM client library in use is:\n"
                        + System.getProperty("messagepigeon.mqjar", "(on the class path)")
                        + "\n\nUse a complete copy, for example the one from an IBM MQ installation. You can choose a "
                        + "different one with Help > IBM MQ Client Library..., then restart.";
            }
        }
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof MQException mqe) {
                StringBuilder sb = new StringBuilder("MQ error: completion " + mqe.completionCode + ", reason "
                        + mqe.reasonCode + " (" + MQConstants.lookup(mqe.reasonCode, "MQRC_.*") + ")");
                Throwable cause = mqe.getCause();
                if (cause != null) {
                    sb.append("\nCaused by: ").append(cause);
                }
                return sb.toString();
            }
        }
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    /** The folder last used in any file or folder chooser; kept for this run only. */
    private static java.io.File lastFolder;

    private static void remember(JFileChooser fc) {
        java.io.File sel = fc.getSelectedFile();
        java.io.File dir = sel != null && sel.isDirectory() ? sel : sel != null ? sel.getParentFile() : null;
        if (dir == null) {
            dir = fc.getCurrentDirectory();
        }
        if (dir != null && dir.isDirectory()) {
            lastFolder = dir;
        }
    }

    private static JFileChooser newChooser(String title) {
        JFileChooser fc = new JFileChooser();
        if (lastFolder != null && lastFolder.isDirectory()) {
            fc.setCurrentDirectory(lastFolder);
        }
        fc.setDialogTitle(title);
        return fc;
    }

    static Path chooseFolder(Component parent, String title) {
        JFileChooser fc = newChooser(title);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        remember(fc);
        return fc.getSelectedFile().toPath();
    }

    /** A file chooser titled for what the file is for (not the stock "Open" / "Save"). */
    static Path choose(Component parent, String title, boolean save) {
        JFileChooser fc = newChooser(title);
        int r = save ? fc.showSaveDialog(parent) : fc.showOpenDialog(parent);
        if (r != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        remember(fc);
        return fc.getSelectedFile().toPath();
    }
}
