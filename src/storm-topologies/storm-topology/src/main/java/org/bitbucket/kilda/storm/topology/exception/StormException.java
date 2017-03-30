package org.bitbucket.kilda.storm.topology.exception;

public class StormException extends RuntimeException {
	
	public StormException(String message, Throwable t) {
		super(message, t);
	}

}
