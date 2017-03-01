package org.bitbucket.openkilda.tools.maxinet.impl;

import java.io.IOException;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Response;

import org.bitbucket.openkilda.tools.maxinet.exception.MaxinetClientException;
import org.bitbucket.openkilda.tools.maxinet.exception.MaxinetException;
import org.bitbucket.openkilda.tools.maxinet.exception.MaxinetInternalException;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ErrorResponseFilter implements ClientResponseFilter {

	private static ObjectMapper _MAPPER = new ObjectMapper();
	
	@Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        
		// TODO - add other non-error responses here
        if (responseContext.getStatus() != Response.Status.OK.getStatusCode() &&
        		responseContext.getStatus() != Response.Status.CREATED.getStatusCode()) {
            if (responseContext.hasEntity()) {
                // get the "real" error message
                Error error = _MAPPER.readValue(responseContext.getEntityStream(), Error.class);

                Response.Status status = Response.Status.fromStatusCode(responseContext.getStatus());
                MaxinetException maxinetException;
                switch (status) {
                    case INTERNAL_SERVER_ERROR:
                    	maxinetException = new MaxinetInternalException(error.getMessage(), responseContext.getStatus());
                        break;
                    case BAD_REQUEST:
                    	maxinetException = new MaxinetClientException(error.getMessage(), responseContext.getStatus());
                        break;
                    default:
                    	maxinetException = new MaxinetException(error.getMessage(), responseContext.getStatus());
                }

                throw maxinetException;
            }
        }
    }

}
