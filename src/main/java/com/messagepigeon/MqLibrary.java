package com.messagepigeon;

import java.awt.Component;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipFile;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Finds the IBM MQ client library (com.ibm.mq.allclient.jar). IBM licenses it separately, so it is not bundled with
 * this program: the user supplies their own copy. This class deliberately refers to nothing in com.ibm.*, because it
 * runs before that library can be loaded.
 */
final class MqLibrary {
    /** Where the chosen jar is remembered, inside the settings folder. */
    static final String SETTING = "mq-client-jar.txt";
    /** A jar can also be named with -Dmessagepigeon.mqjar=... or the MESSAGEPIGEON_MQ_JAR environment variable. */
    static final String PROPERTY = "messagepigeon.mqjar";

    private MqLibrary() {}

    /** The program's settings folder. */
    static Path settingsDir() {
        return Path.of(System.getProperty("user.home"), ".messagepigeon");
    }

    /** True if the jar contains the classes this program uses from IBM's client. */
    static boolean looksLikeClient(Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return false;
        }
        try (ZipFile z = new ZipFile(jar.toFile())) {
            return z.getEntry("com/ibm/mq/MQQueueManager.class") != null
                    && z.getEntry("com/ibm/mq/constants/MQConstants.class") != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** The jar chosen last time, whether or not it is still there; null if none was ever chosen. */
    static Path remembered(Path settingsDir) {
        Path saved = settingsDir.resolve(SETTING);
        if (Files.isRegularFile(saved)) {
            try {
                String s = Files.readString(saved).trim();
                return s.isEmpty() ? null : Path.of(s);
            } catch (IOException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * The first valid client jar found: named explicitly, remembered from last time, next to the program, or in an IBM
     * MQ installation. Null if none is found, including when the remembered one has been moved or deleted.
     */
    static Path find(Path settingsDir, Path appDir) {
        List<Path> candidates = new ArrayList<>();
        addIfSet(candidates, System.getProperty(PROPERTY));
        addIfSet(candidates, System.getenv("MESSAGEPIGEON_MQ_JAR"));
        Path saved = remembered(settingsDir);
        if (saved != null) {
            candidates.add(saved);
        }
        if (appDir != null) {
            candidates.addAll(clientJarsIn(appDir.resolve("lib")));
            candidates.addAll(clientJarsIn(appDir));
        }
        // An IBM MQ installation on this machine.
        List<Path> installs = new ArrayList<>();
        addDir(installs, System.getenv("MQ_JAVA_INSTALL_PATH"), "lib");
        addDir(installs, System.getenv("MQ_INSTALLATION_PATH"), "java/lib");
        addDir(installs, System.getenv("ProgramFiles"), "IBM/MQ/java/lib");
        addDir(installs, "/opt/mqm", "java/lib");
        for (Path dir : installs) {
            candidates.addAll(clientJarsIn(dir));
        }
        for (Path c : candidates) {
            if (looksLikeClient(c)) {
                return c;
            }
        }
        return null;
    }

    private static void addDir(List<Path> into, String root, String relative) {
        if (root != null && !root.isBlank()) {
            into.add(Path.of(root.trim(), relative));
        }
    }

    private static void addIfSet(List<Path> into, String path) {
        if (path != null && !path.isBlank()) {
            into.add(Path.of(path.trim().replace("\"", "")));
        }
    }

    /** com.ibm.mq.allclient*.jar files in a folder, newest name first. */
    private static List<Path> clientJarsIn(Path dir) {
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return found;
        }
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, "com.ibm.mq.allclient*.jar")) {
            s.forEach(found::add);
        } catch (IOException ignored) {
            // not readable: treat as empty
        }
        found.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        return found;
    }

    /**
     * Asks the user to locate the jar, repeating until they give a valid one or cancel. A valid choice is remembered.
     *
     * @param start where the file chooser opens (for example the previous location), or null
     * @return the chosen jar, or null if they cancelled
     */
    static Path choose(Component parent, Path settingsDir, String title, Path start) {
        title = "IBM MQ Client Library"; // what the question is about, whichever program name the caller passed
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Locate com.ibm.mq.allclient.jar");
        chooser.setFileFilter(new FileNameExtensionFilter("Java archives (*.jar)", "jar"));
        if (start != null) {
            Path dir = Files.isDirectory(start) ? start : start.getParent();
            if (dir != null && Files.isDirectory(dir)) {
                chooser.setCurrentDirectory(dir.toFile());
            }
        }
        while (true) {
            if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return null;
            }
            Path jar = chooser.getSelectedFile().toPath();
            if (looksLikeClient(jar)) {
                remember(settingsDir, jar);
                return jar;
            }
            if (JOptionPane.showConfirmDialog(parent, "That file does not look like IBM's MQ client library "
                    + "(it has no com.ibm.mq.MQQueueManager class):\n" + jar + "\n\nTry another file?",
                    title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
                return null;
            }
        }
    }

    /**
     * Start-up question: explains what is needed (and, if a jar was chosen before but has been moved or deleted, says
     * so) and then asks the user to locate it. Returns null if they cancel.
     *
     * @param missing the jar remembered from last time that could not be used, or null
     */
    static Path prompt(Component parent, Path settingsDir, String title, Path missing) {
        String gone = missing == null ? "" : "<b>The copy " + title + " was using can no longer be used</b> (it has "
                + "been moved or deleted, or is not a valid library):<br><code>" + escape(missing.toString())
                + "</code><br><br>Please locate it again.<br><br>";
        String explain = "<html><div style='width:430px'>" + gone + title + " needs IBM's MQ client library, "
                + "<b>com.ibm.mq.allclient.jar</b>. IBM licenses that library itself, so it is not included with this "
                + "program: you supply your own copy.<br><br>"
                + "It is in an IBM MQ installation (the <code>java\\lib</code> folder, for example "
                + "<code>C:\\Program Files\\IBM\\MQ\\java\\lib</code>), in IBM's MQ redistributable client, and on Maven "
                + "Central. IBM's licence terms apply to your copy.<br><br>"
                + "Click OK to locate the file. You can change it later with Help &gt; IBM MQ Client Library.</div></html>";
        if (JOptionPane.showConfirmDialog(parent, explain, title + " - IBM MQ client library",
                JOptionPane.OK_CANCEL_OPTION, missing == null ? JOptionPane.INFORMATION_MESSAGE
                        : JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        return choose(parent, settingsDir, title, missing);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static void remember(Path settingsDir, Path jar) {
        try {
            Files.createDirectories(settingsDir);
            Files.writeString(settingsDir.resolve(SETTING), jar.toAbsolutePath().toString());
        } catch (IOException ignored) {
            // not fatal: the user will simply be asked again next time
        }
    }
}
