package org.bitbucket.openkilda.tools.maxinet.exception;

import javax.ws.rs.WebApplicationException;

public class MaxinetWebException extends WebApplicationException {

	public MaxinetWebException(String message, int status) {
		super(message, status);
	}


}
