package org.openkilda.atdd.staging.service;

public class ExamNotFinishedException extends Exception {
    public ExamNotFinishedException() {
        super("Traffic exam report is not available yet");
    }
}
