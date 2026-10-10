package com.messagepigeon;

import java.awt.*;
import java.io.File;
import java.util.function.Consumer;
import javax.swing.*;

/** Add/edit dialog for a connection profile. */
public final class ProfileDialog extends JDialog {
    private static final String[] PROTOCOLS = {"TLSv1.2", "TLSv1.3"};
    private static final String[] STORE_TYPES = {"PKCS12", "JKS", "JCEKS"};
    private static final String[] SUITES = {
        "", "TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256", "TLS_CHACHA20_POLY1305_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_RSA_WITH_AES_128_GCM_SHA256", "TLS_RSA_WITH_AES_128_CBC_SHA256"
    };

    private final JTextField name = new JTextField(24);
    private final JTextField host = new JTextField(24);
    private final JTextField port = new JTextField(6);
    private final JTextField qmgr = new JTextField(24);
    private final JTextField channel = new JTextField(24);
    private final JTextField user = new JTextField(24);
    private final JPasswordField password = new JPasswordField(24);
    private final JCheckBox tls = new JCheckBox("Use TLS");
    private final JComboBox<String> protocol = new JComboBox<>(PROTOCOLS);
    private final JComboBox<String> suite = new JComboBox<>(SUITES);
    private final JTextField peer = new JTextField(24);
    private final JTextField keyStore = new JTextField(24);
    private final JComboBox<String> keyStoreType = new JComboBox<>(STORE_TYPES);
    private final JPasswordField keyStorePw = new JPasswordField(24);
    private final JTextField trustStore = new JTextField(24);
    private final JComboBox<String> trustStoreType = new JComboBox<>(STORE_TYPES);
    private final JPasswordField trustStorePw = new JPasswordField(24);
    private final ConnectionProfile profile;
    private final Consumer<ConnectionProfile> onSave;

    public ProfileDialog(Frame owner, ConnectionProfile p, Consumer<ConnectionProfile> onSave) {
        super(owner, "Connection Profile", true);
        this.profile = p.copy();
        this.onSave = onSave;
        suite.setEditable(true);
        ((javax.swing.text.AbstractDocument) port.getDocument()).setDocumentFilter(new DigitsOnlyFilter(5));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        int[] row = {0};
        addRow(form, row, "Profile name", name);
        addRow(form, row, "Queue manager", qmgr);
        addRow(form, row, "Host", host);
        addRow(form, row, "Port", port);
        addRow(form, row, "Channel", channel);
        addRow(form, row, "User ID", user);
        addRow(form, row, "Password", password);
        addRow(form, row, "", tls);
        addRow(form, row, "TLS protocol", protocol);
        addRow(form, row, "Cipher suite", suite);
        addRow(form, row, "Peer name (DN)", peer);
        addRow(form, row, "Key store (client cert)", withBrowse(keyStore, "Choose the key store (client certificate)"));
        addRow(form, row, "  type", keyStoreType);
        addRow(form, row, "  password", keyStorePw);
        addRow(form, row, "Trust store (server CA)", withBrowse(trustStore, "Choose the trust store (server certificate authority)"));
        addRow(form, row, "  type", trustStoreType);
        addRow(form, row, "  password", trustStorePw);
        JLabel note = new JLabel("<html><i>Passwords are stored encrypted. Leave the password blank to be asked at "
                + "connect time.<br>Cipher suites use Java (JSSE) names; blank lets TLS negotiate.</i></html>");
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row[0]++;
        c.gridwidth = 2;
        c.insets = new Insets(8, 0, 0, 0);
        form.add(note, c);

        tls.addActionListener(e -> updateTlsEnabled());

        JButton ok = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> save());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);

        setLayout(new BorderLayout());
        add(new JScrollPane(form), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        load();
        autoDetectType(keyStore, keyStoreType); // after load() so a saved type is not overridden
        autoDetectType(trustStore, trustStoreType);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Lets only digits through (typed or pasted), up to a maximum length. */
    static final class DigitsOnlyFilter extends javax.swing.text.DocumentFilter {
        private final int maxLength;

        DigitsOnlyFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, javax.swing.text.AttributeSet attr)
                throws javax.swing.text.BadLocationException {
            replace(fb, offset, 0, text, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attr)
                throws javax.swing.text.BadLocationException {
            String digits = text == null ? "" : text.replaceAll("\\D", "");
            int room = maxLength - (fb.getDocument().getLength() - length);
            if (room <= 0 && !digits.isEmpty()) {
                return;
            }
            if (digits.length() > room) {
                digits = digits.substring(0, Math.max(room, 0));
            }
            super.replace(fb, offset, length, digits, attr);
        }
    }

    private static void addRow(JPanel p, int[] row, String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row[0]++;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);
        p.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(field, c);
    }

    /** The folder last browsed in, kept for the rest of the run so the next chooser opens where the last one ended. */
    private static File lastFolder;

    private JComponent withBrowse(JTextField field, String title) {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        JButton b = new JButton("...");
        b.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle(title);
            File start = startFolder(field.getText(), lastFolder, keyStore.getText(), trustStore.getText());
            if (start != null) {
                fc.setCurrentDirectory(start);
            }
            File typed = fileFrom(field.getText());
            // Pre-select a file already named in the box. A folder is only where to open: selecting it would make
            // the chooser jump to its parent instead.
            if (typed != null && !typed.isDirectory()) {
                fc.setSelectedFile(typed);
            }
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(fc.getSelectedFile().getAbsolutePath());
                lastFolder = fc.getCurrentDirectory();
            }
        });
        p.add(field, BorderLayout.CENTER);
        p.add(b, BorderLayout.EAST);
        return p;
    }

    /**
     * Where a file chooser should open: the folder of the file already in this field, else the folder last browsed in,
     * else the folder of another store already chosen on this form (a key store and its trust store usually live
     * together). Null leaves it to the chooser's default.
     */
    static File startFolder(String ownPath, File lastUsed, String... otherPaths) {
        File own = folderOf(ownPath);
        if (own != null) {
            return own;
        }
        if (lastUsed != null && lastUsed.isDirectory()) {
            return lastUsed;
        }
        for (String other : otherPaths) {
            File f = folderOf(other);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    /** The file or folder named by text typed or chosen into a box (surrounding quotes and spaces ignored), or null. */
    private static File fileFrom(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.trim().replace("\"", "").trim();
        return cleaned.isEmpty() ? null : new File(cleaned);
    }

    /** The folder a path points at, or the folder it is in, provided that folder exists. */
    private static File folderOf(String path) {
        File f = fileFrom(path);
        if (f == null) {
            return null;
        }
        if (f.isDirectory()) {
            return f;
        }
        File parent = f.getParentFile();
        return parent != null && parent.isDirectory() ? parent : null;
    }

    /** Sets the type combo from the file extension whenever the path is edited or chosen. */
    private static void autoDetectType(JTextField path, JComboBox<String> type) {
        path.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void check() {
                String t = typeForExtension(path.getText().trim());
                if (t != null && !t.equals(type.getSelectedItem())) {
                    type.setSelectedItem(t);
                }
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                check();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                check();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                check();
            }
        });
    }

    static String typeForExtension(String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        if (p.endsWith(".jks")) {
            return "JKS";
        }
        if (p.endsWith(".jceks") || p.endsWith(".jck")) {
            return "JCEKS";
        }
        if (p.endsWith(".p12") || p.endsWith(".pfx") || p.endsWith(".pkcs12")) {
            return "PKCS12";
        }
        return null;
    }

    private void updateTlsEnabled() {
        boolean on = tls.isSelected();
        for (JComponent c : new JComponent[] {protocol, suite, peer, keyStore, keyStoreType, keyStorePw, trustStore,
            trustStoreType, trustStorePw}) {
            c.setEnabled(on);
        }
    }

    private void load() {
        name.setText(profile.name);
        host.setText(profile.host);
        port.setText(Integer.toString(profile.port));
        qmgr.setText(profile.queueManager);
        channel.setText(profile.channel);
        user.setText(profile.userId);
        password.setText(profile.password);
        tls.setSelected(profile.tlsEnabled);
        protocol.setSelectedItem(profile.tlsProtocol);
        suite.setSelectedItem(profile.cipherSuite);
        peer.setText(profile.peerName);
        keyStore.setText(profile.keyStorePath);
        keyStoreType.setSelectedItem(profile.keyStoreType);
        keyStorePw.setText(profile.keyStorePassword);
        trustStore.setText(profile.trustStorePath);
        trustStoreType.setSelectedItem(profile.trustStoreType);
        trustStorePw.setText(profile.trustStorePassword);
        updateTlsEnabled();
    }

    private void save() {
        if (name.getText().isBlank() || host.getText().isBlank() || qmgr.getText().isBlank()
                || channel.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Name, host, queue manager and channel are required.", "Connection Profile",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int portValue;
        try {
            portValue = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException e) {
            portValue = 0;
        }
        if (portValue < 1 || portValue > 65535) {
            JOptionPane.showMessageDialog(this, "The port must be a number between 1 and 65535.", "Connection Profile",
                    JOptionPane.WARNING_MESSAGE);
            port.requestFocusInWindow();
            return;
        }
        profile.name = name.getText().trim();
        profile.host = host.getText().trim();
        profile.port = portValue;
        profile.queueManager = qmgr.getText().trim();
        profile.channel = channel.getText().trim();
        profile.userId = user.getText().trim();
        profile.password = new String(password.getPassword());
        profile.tlsEnabled = tls.isSelected();
        profile.tlsProtocol = (String) protocol.getSelectedItem();
        Object s = suite.getSelectedItem();
        profile.cipherSuite = s == null ? "" : s.toString().trim();
        profile.peerName = peer.getText().trim();
        profile.keyStorePath = keyStore.getText().trim();
        profile.keyStoreType = (String) keyStoreType.getSelectedItem();
        profile.keyStorePassword = new String(keyStorePw.getPassword());
        profile.trustStorePath = trustStore.getText().trim();
        profile.trustStoreType = (String) trustStoreType.getSelectedItem();
        profile.trustStorePassword = new String(trustStorePw.getPassword());
        if (profile.tlsEnabled && profile.cipherSuite.isEmpty()) {
            // MQ needs a cipher to switch TLS on; choose one that matches the selected protocol.
            profile.cipherSuite = "TLSv1.3".equals(profile.tlsProtocol)
                    ? "TLS_AES_256_GCM_SHA384" : "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384";
        }
        onSave.accept(profile);
        dispose();
    }
}
