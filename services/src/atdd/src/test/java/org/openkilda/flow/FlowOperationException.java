package org.openkilda.flow;

import javax.ws.rs.core.Response;

public class FlowOperationException extends Exception {
    private final Response restResponse;

    public FlowOperationException(Response restResponse, String s) {
        super(s);

        this.restResponse = restResponse;
    }

    public Response getRestResponse() {
        return restResponse;
    }
}
