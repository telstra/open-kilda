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

package org.openkilda.messaging.info.grpc;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class DeleteLogicalPortResponse extends InfoData {

    @JsonProperty("switch_address")
    private String switchAddress;

    @JsonProperty("logical_port")
    private int logicalPortNumber;

    @JsonProperty("deleted")
    private boolean deleted;

    @JsonCreator
    public DeleteLogicalPortResponse(@JsonProperty("switch_address") String switchAddress,
                                     @JsonProperty("logical_port") int logicalPortNumber,
                                     @JsonProperty("deleted") boolean deleted) {
        this.switchAddress = switchAddress;
        this.logicalPortNumber = logicalPortNumber;
        this.deleted = deleted;
    }
}
