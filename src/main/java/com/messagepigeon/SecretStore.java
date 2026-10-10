package com.messagepigeon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts stored passwords with AES-256-GCM. The key is a random 256-bit value created on first use and kept in a
 * file that only the current OS user can read. This keeps passwords out of plaintext in profiles.properties (and out
 * of backups/copies of that file) but is not protection against someone who can already read the user's profile
 * directory, because the key lives beside the data.
 */
public final class SecretStore {
    private static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;

    public SecretStore(Path dir) throws IOException {
        Files.createDirectories(dir);
        restrictToOwner(dir);
        Path keyFile = dir.resolve("secret.key");
        if (!Files.exists(keyFile)) {
            Files.createFile(keyFile);
            restrictToOwner(keyFile); // lock down before the key material is written
            byte[] k = new byte[32];
            random.nextBytes(k);
            Files.write(keyFile, k);
        }
        byte[] k = Files.readAllBytes(keyFile);
        if (k.length != 32) {
            throw new IOException("Corrupt key file: " + keyFile);
        }
        key = new SecretKeySpec(k, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** @return the plaintext, or empty if the value is empty. Throws if the value is not something we encrypted. */
    public String decrypt(String stored) throws GeneralSecurityException {
        if (stored == null || stored.isEmpty()) {
            return "";
        }
        if (!stored.startsWith(PREFIX)) {
            throw new GeneralSecurityException("Value is not in encrypted form");
        }
        byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
        return new String(c.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
    }

    private static void restrictToOwner(Path p) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(p, PosixFileAttributeView.class);
        if (posix != null) {
            Set<PosixFilePermission> perms = Files.isDirectory(p)
                    ? PosixFilePermissions.fromString("rwx------")
                    : PosixFilePermissions.fromString("rw-------");
            posix.setPermissions(perms);
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(p, AclFileAttributeView.class);
        if (acl != null) {
            UserPrincipal owner = Files.getOwner(p);
            AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
            acl.setAcl(List.of(entry));
        }
    }
}
