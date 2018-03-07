package org.openkilda.atdd.staging.model.traffexam;

import org.openkilda.atdd.staging.utils.Inet4Network;

public class ExamResources {
    private final Inet4Network ipSubnet;
    private final Endpoint producer;
    private final Endpoint consumer;

    public ExamResources(Inet4Network ipSubnet, Endpoint producer,
            Endpoint consumer) {
        this.ipSubnet = ipSubnet;
        this.producer = producer;
        this.consumer = consumer;
    }

    public Inet4Network getIpSubnet() {
        return ipSubnet;
    }

    public Endpoint getProducer() {
        return producer;
    }

    public Endpoint getConsumer() {
        return consumer;
    }
}
