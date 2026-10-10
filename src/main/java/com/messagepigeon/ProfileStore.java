package com.messagepigeon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;

/** Persists connection profiles to ~/.messagepigeon/profiles.properties with secret fields encrypted. */
public final class ProfileStore {
    private final Path file;
    private final SecretStore secrets;

    public ProfileStore(Path dir, SecretStore secrets) {
        this.file = dir.resolve("profiles.properties");
        this.secrets = secrets;
    }

    public List<ConnectionProfile> load() throws IOException {
        List<ConnectionProfile> out = new ArrayList<>();
        if (!Files.exists(file)) {
            return out;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        int count = Integer.parseInt(props.getProperty("count", "0"));
        for (int i = 0; i < count; i++) {
            Map<String, String> m = new HashMap<>();
            String prefix = "p." + i + ".";
            for (String k : props.stringPropertyNames()) {
                if (k.startsWith(prefix)) {
                    String field = k.substring(prefix.length());
                    String v = props.getProperty(k);
                    if (ConnectionProfile.SECRET_FIELDS.contains(field)) {
                        try {
                            v = secrets.decrypt(v);
                        } catch (Exception e) {
                            v = ""; // unreadable (e.g. key file replaced): the user will be prompted
                        }
                    }
                    m.put(field, v);
                }
            }
            out.add(ConnectionProfile.fromMap(m));
        }
        return out;
    }

    private static final String SHARED_FORMAT = "messagepigeon-profiles-1";

    /** Writes profiles to a file to share or keep. Passwords are left out entirely, not even encrypted. */
    public static void exportTo(List<ConnectionProfile> profiles, Path target) throws IOException {
        Properties props = new Properties();
        props.setProperty("format", SHARED_FORMAT);
        props.setProperty("count", Integer.toString(profiles.size()));
        for (int i = 0; i < profiles.size(); i++) {
            for (Map.Entry<String, String> e : profiles.get(i).toSharedMap().entrySet()) {
                props.setProperty("p." + i + "." + e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
        }
        try (OutputStream out = Files.newOutputStream(target)) {
            props.store(out, "MessagePigeon connection profiles (no passwords)");
        }
    }

    /** Reads a file written by {@link #exportTo}. */
    public static List<ConnectionProfile> importFrom(Path source) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(source)) {
            props.load(in);
        }
        if (!SHARED_FORMAT.equals(props.getProperty("format"))) {
            throw new IOException("This is not a MessagePigeon profiles file.");
        }
        List<ConnectionProfile> out = new ArrayList<>();
        int count;
        try {
            count = Integer.parseInt(props.getProperty("count", "0"));
        } catch (NumberFormatException e) {
            throw new IOException("The profiles file is damaged (bad count).");
        }
        for (int i = 0; i < count; i++) {
            Map<String, String> m = new HashMap<>();
            String prefix = "p." + i + ".";
            for (String k : props.stringPropertyNames()) {
                if (k.startsWith(prefix)) {
                    String field = k.substring(prefix.length());
                    if (!ConnectionProfile.SECRET_FIELDS.contains(field)) { // never trust a file to carry secrets
                        m.put(field, props.getProperty(k));
                    }
                }
            }
            out.add(ConnectionProfile.fromMap(m));
        }
        return out;
    }

    public void save(List<ConnectionProfile> profiles) throws IOException {
        Properties props = new Properties();
        props.setProperty("count", Integer.toString(profiles.size()));
        for (int i = 0; i < profiles.size(); i++) {
            for (Map.Entry<String, String> e : profiles.get(i).toMap().entrySet()) {
                String v = e.getValue() == null ? "" : e.getValue();
                if (ConnectionProfile.SECRET_FIELDS.contains(e.getKey())) {
                    v = secrets.encrypt(v);
                }
                props.setProperty("p." + i + "." + e.getKey(), v);
            }
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            props.store(out, "MessagePigeon profiles (secrets are encrypted)");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
