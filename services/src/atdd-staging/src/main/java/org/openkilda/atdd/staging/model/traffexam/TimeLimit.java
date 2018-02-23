package org.openkilda.atdd.staging.model.traffexam;

import java.io.Serializable;

public class TimeLimit implements Serializable {
    private final long valueSeconds;

    public TimeLimit(long value) {
        this.valueSeconds = value;
    }

    public long getSeconds() {
        return valueSeconds;
    }
}
