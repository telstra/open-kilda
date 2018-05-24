package org.openkilda.wfm.topology.stats;

import org.openkilda.wfm.error.AbstractException;

public class FlowCookieException extends AbstractException {
    public FlowCookieException() {
        super("Exception raised with cookie");
    }

    public FlowCookieException(String s) {
        super(s);
    }
}
