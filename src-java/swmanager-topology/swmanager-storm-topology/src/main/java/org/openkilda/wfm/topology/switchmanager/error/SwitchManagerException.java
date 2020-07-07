/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.error;

import org.openkilda.messaging.error.ErrorType;

import lombok.Getter;

@Getter
public class SwitchManagerException extends RuntimeException {
    private final ErrorType error;

    protected SwitchManagerException(ErrorType error, String message) {
        super(message);
        this.error = error;
    }

    public SwitchManagerException(Throwable cause) {
        super(cause.getMessage(), cause);
        this.error = ErrorType.INTERNAL_ERROR;
    }
}
