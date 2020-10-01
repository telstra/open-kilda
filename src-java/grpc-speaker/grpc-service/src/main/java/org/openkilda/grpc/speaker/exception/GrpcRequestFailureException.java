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

package org.openkilda.grpc.speaker.exception;

import org.openkilda.messaging.error.ErrorType;

public class GrpcRequestFailureException extends RuntimeException {

    private final String message;

    private final Integer code;

    private final ErrorType errorType;

    public GrpcRequestFailureException(Integer code, String message, ErrorType errorType) {
        super(message);
        this.message = message;
        this.code = code;
        this.errorType = errorType;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public Integer getCode() {
        return code;
    }

    public ErrorType getErrorType() {
        return errorType;
    }
}
