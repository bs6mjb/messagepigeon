package com.messagepigeon;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The export file: plain UTF-8 text you can open in any editor. Each message is a block of "name: value" header
 * lines, a "---" line, then the message text exactly as it is on the queue, then a boundary line:
 *
 * <pre>
 * === MESSAGE 1 ===
 * format: MQSTR
 * ccsid: 1208
 * ...
 * boundary: === END OF MESSAGE ===
 * ---
 * the message text
 * === END OF MESSAGE ===
 * </pre>
 *
 * Binary messages (or text that would not survive being decoded and re-encoded) are written as Base64 instead and
 * marked "data-encoding: base64".
 */
final class ExportFormat {
    static final String HEADER = "#MESSAGEPIGEON-EXPORT-V2";
    private static final String BOUNDARY = "=== END OF MESSAGE ===";
    private static final String MESSAGE_MARK = "=== MESSAGE";

    private ExportFormat() {}

    static void writeHeader(Writer w, String source) throws IOException {
        w.write(HEADER + "\n");
        w.write("# MessagePigeon export of " + source + ", written "
                + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n");
        w.write("# Each message: header lines, a line of '---', the message text, then the boundary line.\n");
        w.write("# You can edit the text. Fields marked (info) are ignored on import.\n\n");
    }

    static void writeMessage(Writer w, int index, MessageRecord r) throws IOException {
        Charset cs = MessageRecord.charsetFor(r.ccsid);
        String text = new String(r.data, cs);
        boolean asText = java.util.Arrays.equals(text.getBytes(cs), r.data);
        String body = asText ? text : wrap(Base64.getEncoder().encodeToString(r.data));
        String boundary = BOUNDARY;
        while (body.contains(boundary)) {
            boundary += "="; // never let the message text contain its own end marker
        }
        w.write(MESSAGE_MARK + " " + index + " ===\n");
        w.write("format: " + r.format + "\n");
        w.write("ccsid: " + r.ccsid + "\n");
        w.write("encoding: " + r.encoding + "\n");
        w.write("persistence: " + (r.persistence == 1 ? "persistent" : "non-persistent") + "\n");
        w.write("priority: " + r.priority + "\n");
        w.write("msgType: " + r.msgType + "\n");
        w.write("correlId: " + MessageRecord.hex(r.correlId) + "\n");
        w.write("replyToQueue: " + r.replyToQueue + "\n");
        w.write("replyToQueueManager: " + r.replyToQueueManager + "\n");
        w.write("msgId (info): " + MessageRecord.hex(r.msgId) + "\n");
        if (r.putDateTime != null) {
            w.write("putDateTime (info): " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(r.putDateTime) + "\n");
        }
        w.write("putBy (info): " + r.userId + " " + r.putApplication + "\n");
        w.write("data-encoding: " + (asText ? "text" : "base64") + "\n");
        w.write("data-bytes (info): " + r.data.length + "\n");
        w.write("boundary: " + boundary + "\n");
        w.write("---\n");
        w.write(body);
        w.write("\n" + boundary + "\n\n");
    }

    private static String wrap(String base64) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base64.length(); i += 76) {
            sb.append(base64, i, Math.min(base64.length(), i + 76)).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /** True if the file starts with the export header. */
    static boolean isExportFile(Path file) throws IOException {
        return HEADER.equals(firstLine(file));
    }

    private static String firstLine(Path file) throws IOException {
        try (var r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String l = r.readLine();
            return l == null ? "" : l.replace("﻿", "").trim();
        }
    }

    static List<MessageRecord> read(Path file) throws IOException {
        if (!isExportFile(file)) {
            throw new IOException("Not a MessagePigeon export file");
        }
        return readV2(Files.readString(file, StandardCharsets.UTF_8).replace("﻿", ""));
    }

    private static List<MessageRecord> readV2(String s) throws IOException {
        List<MessageRecord> out = new ArrayList<>();
        int pos = 0;
        while (true) {
            int mark = findLineStart(s, MESSAGE_MARK, pos);
            if (mark < 0) {
                return out;
            }
            int n = out.size() + 1;
            int p = lineEnd(s, mark) + 1;
            Map<String, String> h = new HashMap<>();
            while (true) {
                if (p >= s.length()) {
                    throw new IOException("Message " + n + ": missing the '---' line before the text");
                }
                int e = lineEnd(s, p);
                String line = s.substring(p, e).replace("\r", "");
                p = e + 1;
                if (line.equals("---")) {
                    break;
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    h.put(line.substring(0, colon).trim().replace(" (info)", ""), line.substring(colon + 1).trim());
                }
            }
            String boundary = h.getOrDefault("boundary", BOUNDARY);
            int end = findBoundary(s, boundary, p - 1);
            if (end < 0) {
                throw new IOException("Message " + n + ": the end line '" + boundary + "' was not found");
            }
            // The text is everything between the '---' line and the newline before the boundary line.
            String body = end < p ? "" : s.substring(p, end);
            if (body.endsWith("\r")) {
                body = body.substring(0, body.length() - 1);
            }
            out.add(toRecord(n, h, body));
            pos = end + 1;
        }
    }

    /** Index of the newline that precedes a line equal to the boundary, searching from {@code from}. */
    private static int findBoundary(String s, String boundary, int from) {
        int i = from;
        while ((i = s.indexOf("\n" + boundary, i)) >= 0) {
            int after = i + 1 + boundary.length();
            if (after >= s.length() || s.charAt(after) == '\n' || s.charAt(after) == '\r') {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static int findLineStart(String s, String prefix, int from) {
        int i = from;
        while (i < s.length()) {
            if (s.startsWith(prefix, i)) {
                return i;
            }
            int nl = s.indexOf('\n', i);
            if (nl < 0) {
                return -1;
            }
            i = nl + 1;
        }
        return -1;
    }

    private static int lineEnd(String s, int from) {
        int e = s.indexOf('\n', from);
        return e < 0 ? s.length() : e;
    }

    private static MessageRecord toRecord(int n, Map<String, String> h, String body) throws IOException {
        try {
            MessageRecord r = new MessageRecord();
            r.format = h.getOrDefault("format", "");
            r.ccsid = Integer.parseInt(h.getOrDefault("ccsid", "1208"));
            r.encoding = Integer.parseInt(h.getOrDefault("encoding", "273"));
            String per = h.getOrDefault("persistence", "persistent").toLowerCase(java.util.Locale.ROOT);
            r.persistence = per.equals("persistent") || per.equals("1") ? 1 : 0;
            r.priority = Integer.parseInt(h.getOrDefault("priority", "0"));
            r.msgType = Integer.parseInt(h.getOrDefault("msgType", "8"));
            r.correlId = MessageRecord.unhex(h.getOrDefault("correlId", ""), 24);
            r.replyToQueue = h.getOrDefault("replyToQueue", "");
            r.replyToQueueManager = h.getOrDefault("replyToQueueManager", "");
            if ("base64".equals(h.get("data-encoding"))) {
                r.data = Base64.getMimeDecoder().decode(body);
            } else {
                r.data = body.getBytes(MessageRecord.charsetFor(r.ccsid));
            }
            return r;
        } catch (IllegalArgumentException e) {
            throw new IOException("Message " + n + ": " + e.getMessage(), e);
        }
    }
}
