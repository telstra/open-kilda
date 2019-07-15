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

package org.openkilda.floodlight.error;

import org.openkilda.model.MeterId;

import org.projectfloodlight.openflow.types.DatapathId;

public class SwitchMeterConflictException extends SwitchOperationException {
    private final MeterId meterId;

    public SwitchMeterConflictException(DatapathId dpId, MeterId meterId) {
        this(dpId, meterId, "meter with same id already exists");
    }

    public SwitchMeterConflictException(DatapathId dpId, MeterId meterId, String details) {
        super(dpId, String.format("Unable to install meter %s on switch %s - %s", meterId, dpId, details));
        this.meterId = meterId;
    }

    public MeterId getMeterId() {
        return meterId;
    }
}
