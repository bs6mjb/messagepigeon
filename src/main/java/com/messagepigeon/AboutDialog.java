package com.messagepigeon;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import javax.swing.*;

/** Help > About. */
final class AboutDialog extends JDialog {
    AboutDialog(Frame owner) {
        super(owner, "About " + Ui.TITLE, true);
        setIconImages(AppIcon.images());

        JPanel body = new JPanel(new BorderLayout(18, 0));
        body.setBorder(BorderFactory.createEmptyBorder(20, 22, 12, 22));

        Image icon = AppIcon.scaled(96);
        if (icon != null) {
            JLabel pic = new JLabel(new ImageIcon(icon));
            pic.setVerticalAlignment(SwingConstants.TOP);
            body.add(pic, BorderLayout.WEST);
        }

        JLabel name = new JLabel(Ui.TITLE);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 22f));
        JLabel version = new JLabel("Version " + App.version());
        JLabel what = new JLabel("<html>A graphical tool for browsing and editing messages on<br>IBM MQ queues.</html>");

        // Each detail is a label and a value. A long value (such as a deep folder path) wraps within a fixed width,
        // so it can never make this window as wide as the path.
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);
        details.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        details.add(detailRow("Java:", System.getProperty("java.vendor") + " " + System.getProperty("java.version")));
        details.add(detailRow("System:", System.getProperty("os.name") + " " + System.getProperty("os.version")));
        details.add(detailRow("Settings:", Path.of(System.getProperty("user.home"), ".messagepigeon").toString()));
        details.add(detailRow("MQ client:", System.getProperty("messagepigeon.mqjar", "(on the class path)")));

        // Trademark notice: this window says "IBM MQ", so it also says whose marks those are.
        JLabel legal = new JLabel("<html><div style='width:330px'>IBM and MQ are trademarks of International Business "
                + "Machines Corporation. " + Ui.TITLE + " is an independent project and is not affiliated with, "
                + "sponsored by or endorsed by IBM.</div></html>");
        legal.setFont(legal.getFont().deriveFont(Font.PLAIN, 11f));
        legal.setForeground(Color.GRAY);
        legal.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[] {name, version, what, details, legal}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(c);
        }
        text.add(Box.createVerticalStrut(4));
        body.add(text, BorderLayout.CENTER);

        JButton licences = new JButton("Third-party licences...");
        licences.addActionListener(e -> showNotices());
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        buttons.add(licences);
        buttons.add(ok);

        setLayout(new BorderLayout());
        add(body, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** The width, in pixels, that a detail's value wraps to. */
    private static final int VALUE_WIDTH = 270;

    private static JComponent detailRow(String name, String value) {
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        JLabel label = new JLabel(name);
        label.setFont(mono);
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setPreferredSize(new Dimension(80, label.getPreferredSize().height));

        JTextArea text = new JTextArea(value);
        text.setEditable(false);
        text.setOpaque(false);
        text.setFont(mono);
        text.setBorder(null);
        text.setLineWrap(true);          // paths have no spaces, so wrap at any character
        text.setWrapStyleWord(false);
        text.setToolTipText(value);
        text.setSize(new Dimension(VALUE_WIDTH, Short.MAX_VALUE));
        text.setPreferredSize(new Dimension(VALUE_WIDTH, text.getPreferredSize().height)); // height for the wrapped text

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(label, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    /** The notices for the software bundled in the jar, and the trademark statement. */
    static String loadNotices() throws IOException {
        try (InputStream in = AboutDialog.class.getResourceAsStream("/legal/THIRD-PARTY-NOTICES.txt")) {
            if (in == null) {
                throw new IOException("legal/THIRD-PARTY-NOTICES.txt is missing from the application");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void showNotices() {
        String notices;
        try {
            notices = loadNotices();
        } catch (IOException e) {
            Ui.error(this, "Could not read the licence notices", e);
            return;
        }
        JDialog d = new JDialog(this, "Third-party licences", true);
        d.setIconImages(AppIcon.images());
        JTextArea area = new JTextArea(notices);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        area.setMargin(new Insets(8, 10, 8, 10));
        JButton close = new JButton("Close");
        close.addActionListener(e -> d.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        south.add(close);
        d.setLayout(new BorderLayout());
        d.add(new JScrollPane(area), BorderLayout.CENTER);
        d.add(south, BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(close);
        d.setSize(720, 540);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }
}
