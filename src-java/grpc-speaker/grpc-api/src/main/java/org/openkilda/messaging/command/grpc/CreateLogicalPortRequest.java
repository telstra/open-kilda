/* Copyright 2021 Telstra Open Source
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

package org.openkilda.messaging.command.grpc;

import org.openkilda.messaging.model.grpc.LogicalPortType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreateLogicalPortRequest extends GrpcBaseRequest {

    @JsonProperty("port_numbers")
    private List<Integer> portNumbers;

    @JsonProperty("logical_port_number")
    private int logicalPortNumber;

    @JsonProperty("type")
    private LogicalPortType type;

    @JsonCreator
    public CreateLogicalPortRequest(@JsonProperty("address") String address,
                                    @JsonProperty("port_numbers") List<Integer> portNumbers,
                                    @JsonProperty("logical_port_number") int logicalPortNumber,
                                    @JsonProperty("type") LogicalPortType type) {
        super(address);
        this.portNumbers = portNumbers;
        this.logicalPortNumber = logicalPortNumber;
        this.type = type;
    }
}
