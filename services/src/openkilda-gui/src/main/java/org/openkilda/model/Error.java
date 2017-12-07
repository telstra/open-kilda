package org.openkilda.model;

/**
 * Represents an error entity.
 */
public class Error {

	/** The code. */
	private int code;

	/** The message. */
	private String message;

	/**
	 * Instantiates a new error.
	 *
	 * @param code
	 *            the code
	 * @param message
	 *            the message
	 */
	public Error(int code, String message) {
		this.code = code;
		this.message = message;
	}

	/**
	 * Gets the code.
	 *
	 * @return the code
	 */
	public int getCode() {
		return code;
	}

	/**
	 * Gets the message.
	 *
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "ExceptionResponse{" + "code=" + code + ", message='" + message
				+ '\'' + '}';
	}
}
