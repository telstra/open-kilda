/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.exception;

import org.openkilda.messaging.error.ErrorType;

import lombok.Getter;

@Getter
public class FlowRequestValidationException extends FlowProcessingException {
    public FlowRequestValidationException(ErrorType errorType, String errorMessage) {
        super(errorType, errorMessage);
    }

    public FlowRequestValidationException(ErrorType errorType, String errorMessage, Throwable cause) {
        super(errorType, errorMessage, cause);
    }

    public FlowRequestValidationException(FlowProcessingException exception) {
        super(exception.getErrorType(), exception.getMessage(), exception.getCause());
    }
}
