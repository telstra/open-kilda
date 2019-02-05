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

package org.openkilda.wfm.topology.grpc.service;

import static java.util.Objects.requireNonNull;

import org.openkilda.messaging.command.bfd.CreateLogicalPortRequest;
import org.openkilda.messaging.command.bfd.GetAllLogicalPortsRequest;
import org.openkilda.wfm.topology.grpc.model.CreatePortRequest;
import org.openkilda.wfm.topology.grpc.model.GetAllPortsRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GrpcRequestBuilderService {

    private final String defaultLogin;
    private final String defaultPassword;

    public GrpcRequestBuilderService(String login, String password) {
        requireNonNull(login, "Login for grpc session is required");
        requireNonNull(password, "Password for grpc session is required");
        this.defaultLogin = login;
        this.defaultPassword = password;
    }

    /**
     * Creates request.
     * @param request request details.
     * @return built request.
     */
    public CreatePortRequest buildCreatePortRequest(CreateLogicalPortRequest request) {
        return new CreatePortRequest(request.getAddress(), defaultLogin, defaultPassword,
                request.getPortNumber(),
                request.getLogicalPortNumber());
    }

    /**
     * Creates request for loading all ports of the switch.
     * @param request request details.
     * @return built request.
     */
    public GetAllPortsRequest buildGetAllPortsRequest(GetAllLogicalPortsRequest request) {
        return new GetAllPortsRequest(request.getAddress(), defaultLogin, defaultPassword);
    }

}
