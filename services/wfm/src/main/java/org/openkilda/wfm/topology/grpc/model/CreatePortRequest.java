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

package org.openkilda.wfm.topology.grpc.model;

import lombok.Value;

@Value
public class CreatePortRequest extends BaseGrpcRequest {
    private int portNumber;
    private int logicalPortNumber;

    public CreatePortRequest(String address, String username, String password, int portNumber,
                             int logicalPortNumber) {
        super(address, username, password);
        this.portNumber = portNumber;
        this.logicalPortNumber = logicalPortNumber;
    }
}
