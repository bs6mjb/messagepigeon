package com.messagepigeon;

import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQPutMessageOptions;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.function.Consumer;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/** All IBM MQ operations. Not thread-safe: callers serialise access (the UI uses a single worker thread). */
public final class MqService implements AutoCloseable {
    private static final int COMMIT_BATCH = 500;

    /** Receives progress from a long operation. {@code total} is -1 when it is not known. Called on the worker thread. */
    @FunctionalInterface
    public interface Progress {
        Progress NONE = (phase, done, total) -> { };

        void step(String phase, int done, int total);
    }

    private MQQueueManager qm;
    private String qmName = "";

    public void connect(ConnectionProfile p) throws MQException, IOException, GeneralSecurityException {
        close();
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(MQConstants.TRANSPORT_PROPERTY, MQConstants.TRANSPORT_MQSERIES_CLIENT);
        props.put(MQConstants.HOST_NAME_PROPERTY, p.host.trim());
        props.put(MQConstants.PORT_PROPERTY, p.port);
        props.put(MQConstants.CHANNEL_PROPERTY, p.channel.trim());
        if (!p.userId.isBlank()) {
            props.put(MQConstants.USER_ID_PROPERTY, p.userId.trim());
            props.put(MQConstants.PASSWORD_PROPERTY, p.password);
            props.put(MQConstants.USE_MQCSP_AUTHENTICATION_PROPERTY, Boolean.TRUE);
        }
        if (p.tlsEnabled) {
            // Cipher suites are given as JSSE names (e.g. TLS_AES_256_GCM_SHA384), not IBM CipherSpec names.
            System.setProperty("com.ibm.mq.cfg.useIBMCipherMappings", "false");
            props.put(MQConstants.SSL_CIPHER_SUITE_PROPERTY, p.cipherSuite.trim());
            props.put(MQConstants.SSL_SOCKET_FACTORY_PROPERTY, buildSslContext(p).getSocketFactory());
            if (!p.peerName.isBlank()) {
                props.put(MQConstants.SSL_PEER_NAME_PROPERTY, p.peerName.trim());
            }
        }
        qm = new MQQueueManager(p.queueManager.trim(), props);
        qmName = p.queueManager.trim();
    }

    private static SSLContext buildSslContext(ConnectionProfile p) throws IOException, GeneralSecurityException {
        KeyManager[] kms = null;
        if (!p.keyStorePath.isBlank()) {
            KeyStore ks = loadStore(p.keyStorePath, p.keyStoreType, p.keyStorePassword);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, p.keyStorePassword.toCharArray());
            kms = kmf.getKeyManagers();
        }
        TrustManager[] tms = null;
        if (!p.trustStorePath.isBlank()) {
            KeyStore ts = loadStore(p.trustStorePath, p.trustStoreType, p.trustStorePassword);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);
            tms = tmf.getTrustManagers();
        }
        SSLContext ctx = SSLContext.getInstance(p.tlsProtocol);
        ctx.init(kms, tms, null);
        return ctx;
    }

    private static KeyStore loadStore(String path, String type, String password)
            throws IOException, GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance(type);
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            ks.load(in, password.isEmpty() ? null : password.toCharArray());
        }
        return ks;
    }

    private MQQueue open(String queue, int options) throws MQException {
        return qm.accessQueue(queue.trim(), options);
    }

    public int depth(String queue) throws MQException {
        MQQueue q = open(queue, MQConstants.MQOO_INQUIRE);
        try {
            return q.getCurrentDepth();
        } finally {
            q.close();
        }
    }

    /** Non-destructively reads up to {@code max} messages (oldest first). */
    public List<MessageRecord> browse(String queue, int max) throws MQException, IOException {
        return browse(queue, max, Progress.NONE);
    }

    /** As {@link #browse(String, int)}, reporting each message read (the total is the lower of the depth and max). */
    public List<MessageRecord> browse(String queue, int max, Progress progress) throws MQException, IOException {
        int total = Math.min(depth(queue), max);
        progress.step("Reading messages", 0, total > 0 ? total : -1);
        List<MessageRecord> out = new ArrayList<>();
        browseEach(queue, max, r -> {
            out.add(r);
            progress.step("Reading messages", out.size(), Math.max(total, out.size()));
        });
        return out;
    }

    private int browseEach(String queue, int max, Consumer<MessageRecord> sink) throws MQException, IOException {
        MQQueue q = open(queue, MQConstants.MQOO_BROWSE);
        int n = 0;
        try {
            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options = MQConstants.MQGMO_NO_WAIT | MQConstants.MQGMO_BROWSE_FIRST | MQConstants.MQGMO_NO_SYNCPOINT;
            while (n < max) {
                MQMessage m = new MQMessage();
                try {
                    q.get(m, gmo);
                } catch (MQException e) {
                    if (e.reasonCode == MQConstants.MQRC_NO_MSG_AVAILABLE) {
                        break;
                    }
                    throw e;
                }
                sink.accept(toRecord(m));
                n++;
                gmo.options = MQConstants.MQGMO_NO_WAIT | MQConstants.MQGMO_BROWSE_NEXT
                        | MQConstants.MQGMO_NO_SYNCPOINT;
            }
        } finally {
            q.close();
        }
        return n;
    }

    private static MessageRecord toRecord(MQMessage m) throws IOException {
        MessageRecord r = new MessageRecord();
        r.msgId = m.messageId;
        r.correlId = m.correlationId;
        r.putDateTime = m.putDateTime == null ? null : m.putDateTime.getTime();
        r.format = m.format == null ? "" : m.format.trim();
        r.ccsid = m.characterSet;
        r.encoding = m.encoding;
        r.persistence = m.persistence;
        r.priority = m.priority;
        r.msgType = m.messageType;
        r.replyToQueue = m.replyToQueueName == null ? "" : m.replyToQueueName.trim();
        r.replyToQueueManager = m.replyToQueueManagerName == null ? "" : m.replyToQueueManagerName.trim();
        r.userId = m.userId == null ? "" : m.userId.trim();
        r.putApplication = m.putApplicationName == null ? "" : m.putApplicationName.trim();
        r.data = new byte[m.getDataLength()];
        m.readFully(r.data);
        return r;
    }

    /** Destructively removes the message with this id. @return false if it was no longer on the queue. */
    public boolean delete(String queue, byte[] msgId) throws MQException {
        MQQueue q = open(queue, MQConstants.MQOO_INPUT_SHARED);
        try {
            MQMessage m = new MQMessage();
            m.messageId = msgId;
            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options = MQConstants.MQGMO_NO_WAIT | MQConstants.MQGMO_NO_SYNCPOINT
                    | MQConstants.MQGMO_ACCEPT_TRUNCATED_MSG;
            gmo.matchOptions = MQConstants.MQMO_MATCH_MSG_ID;
            try {
                q.get(m, gmo, 0);
            } catch (MQException e) {
                if (e.reasonCode == MQConstants.MQRC_NO_MSG_AVAILABLE) {
                    return false;
                }
                if (e.reasonCode != MQConstants.MQRC_TRUNCATED_MSG_ACCEPTED) {
                    throw e;
                }
            }
            return true;
        } finally {
            q.close();
        }
    }

    /** Removes every message from the queue. @return the number removed. */
    public int clear(String queue) throws MQException {
        MQQueue q = open(queue, MQConstants.MQOO_INPUT_SHARED);
        int n = 0;
        try {
            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options = MQConstants.MQGMO_NO_WAIT | MQConstants.MQGMO_NO_SYNCPOINT
                    | MQConstants.MQGMO_ACCEPT_TRUNCATED_MSG;
            while (true) {
                try {
                    q.get(new MQMessage(), gmo, 0);
                    n++;
                } catch (MQException e) {
                    if (e.reasonCode == MQConstants.MQRC_NO_MSG_AVAILABLE) {
                        break;
                    }
                    if (e.reasonCode == MQConstants.MQRC_TRUNCATED_MSG_ACCEPTED) {
                        n++;
                        continue;
                    }
                    throw e;
                }
            }
        } finally {
            q.close();
        }
        return n;
    }

    public int put(String queue, List<MessageRecord> records) throws MQException, IOException {
        return put(queue, records, Progress.NONE);
    }

    public int put(String queue, List<MessageRecord> records, Progress progress) throws MQException, IOException {
        MQQueue q = open(queue, MQConstants.MQOO_OUTPUT);
        int total = records.size();
        MQPutMessageOptions pmo = new MQPutMessageOptions();
        pmo.options = MQConstants.MQPMO_SYNCPOINT;
        int n = 0;
        int inBatch = 0;
        try {
            for (MessageRecord r : records) {
                q.put(toMessage(r), pmo);
                n++;
                if (++inBatch >= COMMIT_BATCH) {
                    qm.commit();
                    inBatch = 0;
                }
                progress.step("Adding messages", n, total);
            }
            progress.step("Committing", n, total);
            qm.commit();
        } catch (MQException | IOException | RuntimeException e) {
            try {
                qm.backout();
            } catch (MQException ignored) {
                // original failure is the one worth reporting
            }
            throw e;
        } finally {
            q.close();
        }
        return n;
    }

    private static MQMessage toMessage(MessageRecord r) throws IOException {
        MQMessage m = new MQMessage();
        m.format = r.format.isEmpty() ? MQConstants.MQFMT_NONE : r.format;
        m.characterSet = r.ccsid;
        m.encoding = r.encoding;
        m.persistence = r.persistence;
        m.priority = r.priority;
        m.messageType = r.msgType;
        m.correlationId = r.correlId;
        m.replyToQueueName = r.replyToQueue;
        m.replyToQueueManagerName = r.replyToQueueManager;
        m.write(r.data);
        return m;
    }

    public static MessageRecord textMessage(String text) {
        MessageRecord r = new MessageRecord();
        r.data = text.getBytes(StandardCharsets.UTF_8);
        return r;
    }

    /**
     * Replaces a message: the original is removed and the edited copy put in one unit of work, so a failure leaves
     * the queue untouched. The copy keeps the original's message id, but MQ cannot update a message in place, so it
     * joins the queue behind the others of its priority.
     *
     * @return false (and changes nothing) if the original is no longer on the queue.
     */
    public boolean replace(String queue, byte[] oldMsgId, MessageRecord updated) throws MQException, IOException {
        MQQueue q = open(queue, MQConstants.MQOO_INPUT_SHARED | MQConstants.MQOO_OUTPUT);
        try {
            MQMessage old = new MQMessage();
            old.messageId = oldMsgId;
            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options = MQConstants.MQGMO_NO_WAIT | MQConstants.MQGMO_SYNCPOINT
                    | MQConstants.MQGMO_ACCEPT_TRUNCATED_MSG;
            gmo.matchOptions = MQConstants.MQMO_MATCH_MSG_ID;
            try {
                q.get(old, gmo, 0);
            } catch (MQException e) {
                if (e.reasonCode == MQConstants.MQRC_NO_MSG_AVAILABLE) {
                    return false;
                }
                if (e.reasonCode != MQConstants.MQRC_TRUNCATED_MSG_ACCEPTED) {
                    throw e;
                }
            }
            MQPutMessageOptions pmo = new MQPutMessageOptions();
            pmo.options = MQConstants.MQPMO_SYNCPOINT;
            MQMessage replacement = toMessage(updated);
            replacement.messageId = oldMsgId; // no MQPMO_NEW_MSG_ID, so MQ keeps the id we supply
            q.put(replacement, pmo);
            qm.commit();
            return true;
        } catch (MQException | IOException | RuntimeException e) {
            try {
                qm.backout();
            } catch (MQException ignored) {
                // the original failure is the one worth reporting
            }
            throw e;
        } finally {
            q.close();
        }
    }

    /** Writes the given messages to a plain-text export file (see {@link ExportFormat}). */
    public static void exportRecords(List<MessageRecord> records, Path file, String source, Progress progress)
            throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            ExportFormat.writeHeader(w, source);
            int n = 0;
            for (MessageRecord r : records) {
                ExportFormat.writeMessage(w, ++n, r);
                progress.step("Exporting messages", n, records.size());
            }
        }
    }

    /** Streams the whole queue to a plain-text export file. @return the number of messages written. */
    public int exportAll(String queue, Path file, Progress progress) throws MQException, IOException {
        progress.step("Counting messages", 0, -1);
        int total = depth(queue); // a guide only: the queue can change while we read it
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            ExportFormat.writeHeader(w, qmName + " / " + queue);
            int[] n = {0};
            return browseEach(queue, Integer.MAX_VALUE, r -> {
                try {
                    ExportFormat.writeMessage(w, ++n[0], r);
                    progress.step("Exporting messages", n[0], Math.max(total, n[0]));
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** True for a MessagePigeon export file. */
    public static boolean isBackupFile(Path file) throws IOException {
        return ExportFormat.isExportFile(file);
    }

    public int importBackup(String queue, Path file, Progress progress) throws MQException, IOException {
        progress.step("Reading the export file", 0, -1);
        return put(queue, ExportFormat.read(file), progress);
    }

    /** The regular, non-hidden files directly inside a folder (subfolders are not searched), sorted by name. */
    public static List<Path> listFiles(Path dir) throws IOException {
        try (java.util.stream.Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        try {
                            return !Files.isHidden(p);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    /** The order files from a folder are added to a queue in. Messages of equal priority are delivered in this order. */
    public enum FileOrder {
        NAME("File name (A to Z)"),
        OLDEST_FIRST("Date modified, oldest first"),
        NEWEST_FIRST("Date modified, newest first");

        final String label;

        FileOrder(String label) {
            this.label = label;
        }
    }

    /**
     * The files in the requested order. Date orders use each file's last-modified time and fall back to the file name
     * (A to Z, ignoring case) for files with the same time, so the result is always predictable.
     */
    public static List<Path> sortFiles(List<Path> files, FileOrder order) throws IOException {
        java.util.Comparator<Path> byName = java.util.Comparator.comparing(p -> p.getFileName().toString(),
                String.CASE_INSENSITIVE_ORDER);
        if (order == FileOrder.NAME) {
            return files.stream().sorted(byName).toList();
        }
        java.util.Map<Path, Long> modified = new java.util.HashMap<>();
        for (Path p : files) {
            modified.put(p, Files.getLastModifiedTime(p).toMillis()); // read each once, not once per comparison
        }
        java.util.Comparator<Path> byTime = java.util.Comparator.comparing(modified::get);
        if (order == FileOrder.NEWEST_FIRST) {
            byTime = byTime.reversed();
        }
        return files.stream().sorted(byTime.thenComparing(byName)).toList();
    }

    /** A file in a folder, with the details the folder dialog needs so it never has to go back to the disk. */
    public record FileInfo(Path path, long size, long modified) { }

    /** Details of the regular, non-hidden files directly inside a folder, in name order. */
    public static List<FileInfo> listFileInfo(Path dir) throws IOException {
        List<FileInfo> out = new ArrayList<>();
        for (Path p : listFiles(dir)) {
            java.nio.file.attribute.BasicFileAttributes a =
                    Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
            out.add(new FileInfo(p, a.size(), a.lastModifiedTime().toMillis()));
        }
        return out;
    }

    /** Sorts file details in memory; date orders fall back to the name (A to Z, ignoring case) for equal times. */
    public static List<FileInfo> sort(List<FileInfo> files, FileOrder order) {
        java.util.Comparator<FileInfo> byName = java.util.Comparator.comparing(
                (FileInfo f) -> f.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER);
        if (order == FileOrder.NAME) {
            return files.stream().sorted(byName).toList();
        }
        java.util.Comparator<FileInfo> byTime = java.util.Comparator.comparingLong(FileInfo::modified);
        if (order == FileOrder.NEWEST_FIRST) {
            byTime = byTime.reversed();
        }
        return files.stream().sorted(byTime.thenComparing(byName)).toList();
    }

    /**
     * Reads a list of file extensions typed by a person: separated by commas, semicolons or spaces, with or without a
     * leading dot or "*." (so "xml, .JSON; *.txt" gives xml, json and txt). Case is ignored. Empty means no filter.
     */
    public static java.util.Set<String> parseExtensions(String text) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (text != null) {
            for (String part : text.split("[,;\\s]+")) {
                String e = part.trim().toLowerCase(java.util.Locale.ROOT);
                while (e.startsWith("*") || e.startsWith(".")) {
                    e = e.substring(1);
                }
                if (!e.isEmpty()) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    /** True if the file's name ends in one of the extensions (".tar.gz" style compound ones work too), or none were given. */
    public static boolean matchesExtensions(Path file, java.util.Set<String> extensions) {
        if (extensions.isEmpty()) {
            return true;
        }
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        for (String e : extensions) {
            if (name.endsWith("." + e)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Turns a whole file into one message. UTF-8 text (no NUL bytes) becomes a text message, with any leading byte
     * order mark dropped; anything else is sent as raw bytes with no format.
     */
    public static MessageRecord fileMessage(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        MessageRecord r = new MessageRecord();
        if (isUtf8Text(data)) {
            boolean bom = data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB
                    && (data[2] & 0xFF) == 0xBF;
            r.data = bom ? java.util.Arrays.copyOfRange(data, 3, data.length) : data;
        } else {
            r.data = data;
            r.format = ""; // MQFMT_NONE: raw bytes
        }
        return r;
    }

    private static boolean isUtf8Text(byte[] data) {
        for (byte b : data) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(data));
            return true;
        } catch (java.nio.charset.CharacterCodingException e) {
            return false;
        }
    }

    /** Reads a text file, one message per non-blank line. */
    public static List<MessageRecord> readLines(Path file) throws IOException {
        List<MessageRecord> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                out.add(textMessage(line));
            }
        }
        return out;
    }

    @Override
    public void close() {
        if (qm != null) {
            try {
                qm.disconnect();
            } catch (MQException ignored) {
                // nothing useful to do
            }
            qm = null;
        }
    }
}
