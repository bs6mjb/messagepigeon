package com.messagepigeon;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

/** Help > Help Contents: shows the bundled help page (help/help.html) in its own window. */
final class HelpWindow extends JFrame {
    private static HelpWindow instance;

    private final JEditorPane pane = new JEditorPane();
    private final String html;

    /** Opens the help window, or brings it to the front if it is already open. */
    static void show(Frame owner) {
        if (instance == null || !instance.isDisplayable()) {
            try {
                instance = new HelpWindow(owner);
            } catch (IOException e) {
                Ui.error(owner, "Could not open the help file", e);
                return;
            }
        }
        instance.setVisible(true);
        instance.toFront();
    }

    /** The help page as HTML text. */
    static String loadHtml() throws IOException {
        try (InputStream in = HelpWindow.class.getResourceAsStream("/help/help.html")) {
            if (in == null) {
                throw new IOException("help/help.html is missing from the application");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private HelpWindow(Frame owner) throws IOException {
        super("MessagePigeon Help");
        setIconImages(AppIcon.images());
        html = loadHtml();

        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setText(html);
        pane.setCaretPosition(0);
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
                return;
            }
            String target = e.getDescription();
            if (target != null && target.startsWith("#")) {
                pane.scrollToReference(target.substring(1));
            } else if (e.getURL() != null) {
                browse(e.getURL().toString());
            }
        });

        JButton top = new JButton("Contents");
        top.addActionListener(e -> pane.scrollToReference("top"));
        JButton browser = new JButton("Open in Browser");
        browser.setToolTipText("Show this help in your web browser, where you can also print it");
        browser.addActionListener(e -> openInBrowser());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.add(top);
        bar.add(browser);

        setLayout(new BorderLayout());
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(pane), BorderLayout.CENTER);
        setSize(900, 760);
        setLocationRelativeTo(owner);
    }

    private void openInBrowser() {
        try {
            Path file = Files.createTempFile("messagepigeon-help-", ".html");
            file.toFile().deleteOnExit();
            Files.writeString(file, html, StandardCharsets.UTF_8);
            browse(file.toUri().toString());
        } catch (IOException e) {
            Ui.error(this, "Could not open the browser", e);
        }
    }

    private void browse(String uri) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(uri));
            }
        } catch (IOException | RuntimeException e) {
            Ui.error(this, "Could not open the link", e);
        }
    }
}
