/* Copyright 2018 Telstra Open Source
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

package org.openkilda.exception;

/**
 * The Class ExternalSystemException.
 */
public class ExternalSystemException extends RuntimeException {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The code. */
    private final Integer code;

    /**
     * Instantiates a new external system exception.
     *
     * @param code the code
     * @param message the message
     * @param cause the cause
     * @param enableSuppression the enable suppression
     * @param writableStackTrace the writable stack trace
     */
    public ExternalSystemException(final Integer code, final String message, final Throwable cause,
            final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.code = code;
    }

    /**
     * Instantiates a new external system exception.
     *
     * @param code the code
     * @param message the message
     * @param cause the cause
     */
    public ExternalSystemException(final Integer code, final String message, final Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Instantiates a new external system exception.
     *
     * @param code the code
     * @param message the message
     */
    public ExternalSystemException(final Integer code, final String message) {
        super(message);
        this.code = code;
    }

    /*
     * Gets the code.
     *
     * @return the code
     */
    public Integer getCode() {
        return code;
    }
}
