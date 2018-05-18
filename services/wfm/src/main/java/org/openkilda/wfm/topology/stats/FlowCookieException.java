package org.openkilda.wfm.topology.stats;

public class FlowCookieException extends Exception {
    public FlowCookieException() {
        super("Exception raised with cookie");
    }

    public FlowCookieException(String s) {
        super(s);
    }
}
