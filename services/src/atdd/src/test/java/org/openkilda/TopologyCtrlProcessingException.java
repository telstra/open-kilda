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

package org.openkilda;

/**
 * {@code TopologyCtrlProcessingException} is thrown when a topology ctrl request/response fails to be processed.
 */
public class TopologyCtrlProcessingException extends RuntimeException {

    public TopologyCtrlProcessingException(String message) {
        super(message);
    }

    public TopologyCtrlProcessingException(String message, Exception cause) {
        super(message, cause);
    }
}
