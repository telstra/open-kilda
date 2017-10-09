/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.tools.maxinet.impl;

import java.io.IOException;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Response;

import org.openkilda.tools.maxinet.exception.MaxinetClientException;
import org.openkilda.tools.maxinet.exception.MaxinetWebException;
import org.openkilda.tools.maxinet.exception.MaxinetInternalException;

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
                MaxinetWebException maxinetException;
                switch (status) {
                    case INTERNAL_SERVER_ERROR:
                    	maxinetException = new MaxinetInternalException(error.getMessage(), responseContext.getStatus());
                        break;
                    case BAD_REQUEST:
                    	maxinetException = new MaxinetClientException(error.getMessage(), responseContext.getStatus());
                        break;
                    default:
                    	maxinetException = new MaxinetWebException(error.getMessage(), responseContext.getStatus());
                }

                throw maxinetException;
            }
        }
    }

}
