package org.openkilda.atdd.utils.controller;

public class DpIdNotFoundException extends Exception {
    public DpIdNotFoundException() {
        super("DpId not found");
    }
}
