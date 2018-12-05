package org.openkilda;

public final class Constants {

    public static final String KAFKA_SERVER = "localhost:9092";

    public static final String STREAM_HUB_BOLT_TO_WORKER_BOLT = "stream-hub-bolt-to-worker-bolt";
    public static final String STREAM_WORKER_BOLT_TO_FL = "stream-worker-bolt-to-fl";
    public static final String STREAM_WORKER_BOLT_TO_HUB_BOLT = "stream-worker-bolt-to-hub-bolt";
    public static final String STREAM_HUB_BOLT_TO_NB = "stream-hub-bolt-to-nb";

    public static final String SPOUT_HUB = "spout-hub";
    public static final String BOLT_HUB = "bolt-hub";
    public static final String SPOUT_WORKER = "spout-worker";
    public static final String BOLT_WORKER = "bolt-worker";
    public static final String BOLT_TO_FL = "bolt-to-fl";
    public static final String BOLT_TO_NB = "bolt-to-nb";

    public static final String TOPIC_NB_TO_HUB = "nb-to-hub";
    public static final String TOPIC_HUB_TO_NB = "hub-to-nb";
    public static final String TOPIC_FL_TO_WORKER = "fl-to-worker";
    public static final String TOPIC_WORKER_TO_FL = "worker-to-fl";

    private Constants() {
    }
}