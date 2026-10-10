package com.messagepigeon;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 * The program's entry point (the jar's Main-Class). IBM's MQ client library is not bundled, so this finds the user's
 * copy first, then starts the application with that library on its class path. If the library is already on the class
 * path (for example when started with -cp), it simply starts the application.
 *
 * <p>Nothing here may refer to a com.ibm.* class, directly or through App: it runs before the library is available.
 */
public final class Launch {
    private Launch() {}

    public static void main(String[] args) throws Exception {
        if (isAvailable(Launch.class.getClassLoader())) {
            start(Launch.class.getClassLoader(), args);
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look and feel is fine for the one question we may need to ask
        }
        Path settingsDir = MqLibrary.settingsDir();
        Path appJar = ownLocation();
        Path client = MqLibrary.find(settingsDir, appJar == null ? null : appJar.getParent());
        if (client == null) {
            // Nothing usable. If a jar was chosen before it has been moved or deleted, so say that and ask again.
            client = MqLibrary.prompt(null, settingsDir, "MessagePigeon", MqLibrary.remembered(settingsDir));
        }
        if (client == null) {
            JOptionPane.showMessageDialog(null, "MessagePigeon cannot start without IBM's MQ client library "
                    + "(com.ibm.mq.allclient.jar).\nRun it again and choose the file when asked.", "MessagePigeon",
                    JOptionPane.WARNING_MESSAGE);
            System.exit(1);
            return;
        }
        System.setProperty(MqLibrary.PROPERTY, client.toAbsolutePath().toString());
        List<Path> path = new ArrayList<>();
        if (appJar != null) {
            path.add(appJar);
        }
        path.add(client);
        start(newLoader(path), args);
    }

    /** A class loader over the given jars or folders, kept apart from the application class path. */
    static URLClassLoader newLoader(List<Path> classPath) throws Exception {
        URL[] urls = new URL[classPath.size()];
        for (int i = 0; i < urls.length; i++) {
            urls[i] = classPath.get(i).toUri().toURL();
        }
        // The platform loader as parent: the program's own classes must come from this loader, beside IBM's.
        return new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
    }

    private static void start(ClassLoader loader, String[] args) throws Exception {
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> app = Class.forName("com.messagepigeon.App", true, loader);
        try {
            app.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private static boolean isAvailable(ClassLoader loader) {
        try {
            Class.forName("com.ibm.mq.MQQueueManager", false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** The jar (or class folder) this program is running from. */
    private static Path ownLocation() {
        try {
            return Path.of(Launch.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }
}
