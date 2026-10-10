package com.messagepigeon;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** One open queue manager connection. MQ handles are not thread-safe, so all work goes through one thread. */
final class Connection {
    final ConnectionProfile profile;
    final MqService mq = new MqService();
    final ExecutorService worker;

    Connection(ConnectionProfile profile) {
        this.profile = profile;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mq-" + profile.name);
            t.setDaemon(true);
            // IBM's client looks up its own resource files through the thread's context class loader. A new thread
            // inherits that from whichever thread created it, which is not reliably the loader that holds IBM's jar
            // (the user supplies it at run time), so it is set explicitly here.
            t.setContextClassLoader(Connection.class.getClassLoader());
            return t;
        });
    }

    /** Disconnects after any queued work finishes. */
    void close() {
        worker.execute(mq::close);
        worker.shutdown();
    }

    void awaitClose(long millis) {
        try {
            worker.awaitTermination(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
