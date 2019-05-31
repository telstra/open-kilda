/* Copyright 2019 Telstra Open Source
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
public class FlowProcessingException extends RuntimeException {

    private final ErrorType errorType;
    private final String errorMessage;
    private final String errorDescription;

    public FlowProcessingException(ErrorType errorType, String errorMessage, String errorDescription) {
        super(errorMessage);

        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.errorDescription = errorDescription;
    }

    public FlowProcessingException(String errorMessage) {
        this(ErrorType.INTERNAL_ERROR, errorMessage, errorMessage);
    }
}
