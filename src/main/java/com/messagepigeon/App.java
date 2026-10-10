package com.messagepigeon;

import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class App {
    private App() {}

    /** The version from the jar manifest, or a note when running from compiled classes. */
    static String version() {
        String v = App.class.getPackage().getImplementationVersion();
        return v == null ? "(development build)" : v;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // The event thread may have been started before the loader holding IBM's jar was chosen; make sure
            // anything it does (and every thread it creates) looks things up in the right loader.
            Thread.currentThread().setContextClassLoader(App.class.getClassLoader());
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // default look and feel is fine
            }
            Ui.tightenMenus();
            try {
                Path dir = Path.of(System.getProperty("user.home"), ".messagepigeon");
                SecretStore secrets = new SecretStore(dir);
                new MainFrame(new ProfileStore(dir, secrets)).setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Cannot start: " + e, "MessagePigeon", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
