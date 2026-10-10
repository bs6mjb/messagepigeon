package com.messagepigeon;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** A message as read from, or to be written to, a queue. */
public final class MessageRecord {
    public byte[] msgId = new byte[24];
    public byte[] correlId = new byte[24];
    public Date putDateTime;
    public String format = "MQSTR";
    public int ccsid = 1208;
    public int encoding = 273;
    public int persistence = 1; // MQPER_PERSISTENT
    public int priority = 0;
    public int msgType = 8; // MQMT_DATAGRAM
    public String replyToQueue = "";
    public String replyToQueueManager = "";
    public String userId = "";
    public String putApplication = "";
    public byte[] data = new byte[0];

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(String.format("%02X", x));
        }
        return sb.toString();
    }

    public static byte[] unhex(String s, int length) {
        byte[] out = new byte[length];
        s = s == null ? "" : s.trim();
        for (int i = 0; i < length && i * 2 + 1 < s.length(); i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static Charset charsetFor(int ccsid) {
        try {
            return switch (ccsid) {
                case 1208 -> StandardCharsets.UTF_8;
                case 1200 -> StandardCharsets.UTF_16BE;
                case 819 -> StandardCharsets.ISO_8859_1;
                case 367 -> StandardCharsets.US_ASCII;
                default -> Charset.forName("Cp" + ccsid);
            };
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    public String text() {
        return new String(data, charsetFor(ccsid));
    }

    public MessageRecord copy() {
        MessageRecord r = new MessageRecord();
        r.msgId = msgId.clone();
        r.correlId = correlId.clone();
        r.putDateTime = putDateTime;
        r.format = format;
        r.ccsid = ccsid;
        r.encoding = encoding;
        r.persistence = persistence;
        r.priority = priority;
        r.msgType = msgType;
        r.replyToQueue = replyToQueue;
        r.replyToQueueManager = replyToQueueManager;
        r.userId = userId;
        r.putApplication = putApplication;
        r.data = data.clone();
        return r;
    }

    public String hexDump() {
        return hexDump(data);
    }

    public static String hexDump(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int off = 0; off < data.length; off += 16) {
            sb.append(String.format("%08X  ", off));
            StringBuilder asc = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                if (off + i < data.length) {
                    int v = data[off + i] & 0xFF;
                    sb.append(String.format("%02X ", v));
                    asc.append(v >= 32 && v < 127 ? (char) v : '.');
                } else {
                    sb.append("   ");
                }
            }
            sb.append(' ').append(asc).append('\n');
        }
        return sb.toString();
    }

    public String preview(int max) {
        String t = text().replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "..." : t;
    }
}
