package org.openkilda.atdd.staging.service.traffexam.model;

import lombok.Value;
import org.openkilda.atdd.staging.service.traffexam.networkpool.Inet4Network;

@Value
public class ExamResources {

    private Inet4Network ipSubnet;
    private Endpoint producer;
    private Endpoint consumer;
}
