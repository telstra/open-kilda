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

package org.openkilda.floodlight.error;

import org.openkilda.floodlight.model.OfBatchResult;
import org.openkilda.floodlight.model.OfRequestResponse;

import java.util.List;
import java.util.stream.Collectors;

public class OfBatchWriteException extends AbstractException {
    private final OfBatchResult result;

    public OfBatchWriteException(OfBatchResult result) {
        super("There is an error during OF write");
        this.result = result;
    }

    public OfBatchResult getResult() {
        return result;
    }

    /**
     * Filter out all but records with error response.
     */
    public List<OfRequestResponse> getErrors() {
        return result.getBatch().stream()
                .filter(OfRequestResponse::isError)
                .collect(Collectors.toList());
    }
}
