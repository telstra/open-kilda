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

package org.openkilda.floodlight.model;

import java.util.List;

public class OfBatchResult {
    private final List<OfRequestResponse> batch;
    private final boolean error;

    public OfBatchResult(List<OfRequestResponse> batch, boolean error) {
        this.batch = batch;
        this.error = error;
    }

    public List<OfRequestResponse> getBatch() {
        return batch;
    }

    public boolean isError() {
        return error;
    }
}
