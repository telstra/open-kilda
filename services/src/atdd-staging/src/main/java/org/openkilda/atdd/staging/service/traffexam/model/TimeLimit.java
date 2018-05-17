package org.openkilda.atdd.staging.service.traffexam.model;

import lombok.Value;

import java.io.Serializable;

@Value
public class TimeLimit implements Serializable {

    private long seconds;
}
