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

import org.usermanagement.exception.CustomException;

/**
 * The Class UnauthorizedException.
 */
public class InvalidOtpException extends CustomException {

    private static final long serialVersionUID = 4768494178056774577L;

    /**
     * Instantiates when user is unauthorized.
     *
     * @param message the message
     */
    public InvalidOtpException(final String message) {
        super(message);
    }

    public InvalidOtpException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidOtpException(int code) {
        super(code);
    }

    public InvalidOtpException(int code, String message) {
        super(code, message);
    }

    public InvalidOtpException(int code, Throwable cause) {
        super(code, cause);
    }

    public InvalidOtpException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public InvalidOtpException(int code, String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(code, message, cause, enableSuppression, writableStackTrace);
    }
}
