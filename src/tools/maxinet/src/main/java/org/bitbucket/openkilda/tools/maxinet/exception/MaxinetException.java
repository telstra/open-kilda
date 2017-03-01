package org.bitbucket.openkilda.tools.maxinet.exception;

import javax.ws.rs.WebApplicationException;

public class MaxinetException extends WebApplicationException {

	public MaxinetException(String message, int status) {
		super(message, status);
	}


}
