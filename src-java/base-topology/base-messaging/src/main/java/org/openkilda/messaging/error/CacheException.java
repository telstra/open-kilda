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

package org.openkilda.messaging.error;


/**
 * The exception for notifying errors.
 */
public class CacheException extends RuntimeException {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The error type.
     */
    protected ErrorType errorType;

    /**
     * The error message.
     */
    protected String errorMessage;

    /**
     * The error description.
     */
    protected String errorDescription;

    /**
     * Instance constructor.
     *
     * @param errorType        error type
     * @param errorMessage     error message
     * @param errorDescription error description
     */
    public CacheException(ErrorType errorType, String errorMessage, String errorDescription) {
        super(errorMessage);
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.errorDescription = errorDescription;
    }

    /**
     * Returns error type.
     *
     * @return error type
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Returns error message.
     *
     * @return error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns error description.
     *
     * @return error description
     */
    public String getErrorDescription() {
        return errorDescription;
    }
}
