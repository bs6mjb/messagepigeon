package com.messagepigeon;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** A saved queue manager connection. Passwords are held in clear only in memory; see {@link SecretStore}. */
public final class ConnectionProfile {
    /** Fields that are encrypted when the profile is written to disk. */
    public static final Set<String> SECRET_FIELDS = Set.of("password", "keyStorePassword", "trustStorePassword");

    public String name = "";
    public String host = "localhost";
    public int port = 1414;
    public String queueManager = "";
    public String channel = "DEV.APP.SVRCONN";
    public String userId = "";
    public String password = "";
    public boolean tlsEnabled;
    public String tlsProtocol = "TLSv1.2";
    public String cipherSuite = "";
    public String peerName = "";
    public String keyStorePath = "";
    public String keyStoreType = "PKCS12";
    public String keyStorePassword = "";
    public String trustStorePath = "";
    public String trustStoreType = "PKCS12";
    public String trustStorePassword = "";
    public String lastQueue = "";
    /** Comma-separated, most recent first. */
    public String recentQueues = "";

    public java.util.List<String> recent() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String q : recentQueues.split(",")) {
            if (!q.isBlank()) {
                out.add(q.trim());
            }
        }
        return out;
    }

    public void rememberQueue(String queue) {
        java.util.List<String> q = recent();
        q.remove(queue);
        q.add(0, queue);
        recentQueues = String.join(",", q.subList(0, Math.min(q.size(), 20)));
        lastQueue = queue;
    }

    /** Removes one queue from the history. @return true if it was there. */
    public boolean forgetQueue(String queue) {
        java.util.List<String> q = recent();
        boolean had = q.remove(queue);
        recentQueues = String.join(",", q);
        if (queue.equals(lastQueue)) {
            lastQueue = q.isEmpty() ? "" : q.get(0); // the most recent one that is left
        }
        return had;
    }

    /** Forgets every remembered queue. */
    public void clearQueueHistory() {
        recentQueues = "";
        lastQueue = "";
    }

    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("host", host);
        m.put("port", Integer.toString(port));
        m.put("queueManager", queueManager);
        m.put("channel", channel);
        m.put("userId", userId);
        m.put("password", password);
        m.put("tlsEnabled", Boolean.toString(tlsEnabled));
        m.put("tlsProtocol", tlsProtocol);
        m.put("cipherSuite", cipherSuite);
        m.put("peerName", peerName);
        m.put("keyStorePath", keyStorePath);
        m.put("keyStoreType", keyStoreType);
        m.put("keyStorePassword", keyStorePassword);
        m.put("trustStorePath", trustStorePath);
        m.put("trustStoreType", trustStoreType);
        m.put("trustStorePassword", trustStorePassword);
        m.put("lastQueue", lastQueue);
        m.put("recentQueues", recentQueues);
        return m;
    }

    /** The fields that are safe and useful to share: no passwords, and none of this user's queue history. */
    public Map<String, String> toSharedMap() {
        Map<String, String> m = toMap();
        m.keySet().removeAll(SECRET_FIELDS);
        m.remove("lastQueue");
        m.remove("recentQueues");
        return m;
    }

    public static ConnectionProfile fromMap(Map<String, String> m) {
        ConnectionProfile p = new ConnectionProfile();
        p.name = m.getOrDefault("name", "");
        p.host = m.getOrDefault("host", "localhost");
        try {
            p.port = Integer.parseInt(m.getOrDefault("port", "1414"));
        } catch (NumberFormatException e) {
            p.port = 1414;
        }
        p.queueManager = m.getOrDefault("queueManager", "");
        p.channel = m.getOrDefault("channel", "");
        p.userId = m.getOrDefault("userId", "");
        p.password = m.getOrDefault("password", "");
        p.tlsEnabled = Boolean.parseBoolean(m.get("tlsEnabled"));
        p.tlsProtocol = m.getOrDefault("tlsProtocol", "TLSv1.2");
        p.cipherSuite = m.getOrDefault("cipherSuite", "");
        p.peerName = m.getOrDefault("peerName", "");
        p.keyStorePath = m.getOrDefault("keyStorePath", "");
        p.keyStoreType = m.getOrDefault("keyStoreType", "PKCS12");
        p.keyStorePassword = m.getOrDefault("keyStorePassword", "");
        p.trustStorePath = m.getOrDefault("trustStorePath", "");
        p.trustStoreType = m.getOrDefault("trustStoreType", "PKCS12");
        p.trustStorePassword = m.getOrDefault("trustStorePassword", "");
        p.lastQueue = m.getOrDefault("lastQueue", "");
        p.recentQueues = m.getOrDefault("recentQueues", "");
        return p;
    }

    public ConnectionProfile copy() {
        return fromMap(toMap());
    }

    @Override
    public String toString() {
        return name;
    }
}
