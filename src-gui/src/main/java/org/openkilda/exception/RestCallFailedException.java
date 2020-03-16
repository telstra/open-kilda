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
 * The Class RestCallFailedException.
 */
public class RestCallFailedException extends RuntimeException {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new rest call failed exception.
     */
    public RestCallFailedException() {
        super();
    }

    /**
     * Instantiates a new rest call failed exception.
     *
     * @param message the message
     * @param cause the cause
     * @param enableSuppression the enable suppression
     * @param writableStackTrace the writable stack trace
     */
    public RestCallFailedException(final String message, final Throwable cause,
            final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Instantiates a new rest call failed exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public RestCallFailedException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Instantiates a new rest call failed exception.
     *
     * @param message the message
     */
    public RestCallFailedException(final String message) {
        super(message);
    }

    /**
     * Instantiates a new rest call failed exception.
     *
     * @param cause the cause
     */
    public RestCallFailedException(final Throwable cause) {
        super(cause);
    }
}
