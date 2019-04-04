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

package org.openkilda.testing.service.grpc;

import org.openkilda.grpc.speaker.model.EnableLogMessagesResponse;
import org.openkilda.grpc.speaker.model.GrpcDeleteOperationResponse;
import org.openkilda.grpc.speaker.model.LicenseDto;
import org.openkilda.grpc.speaker.model.LicenseResponse;
import org.openkilda.grpc.speaker.model.LogMessagesDto;
import org.openkilda.grpc.speaker.model.LogOferrorsDto;
import org.openkilda.grpc.speaker.model.LogicalPortDto;
import org.openkilda.grpc.speaker.model.RemoteLogServerDto;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.RemoteLogServer;
import org.openkilda.messaging.model.grpc.SwitchInfoStatus;

import java.util.List;

public interface GrpcService {
    SwitchInfoStatus getSwitchStatus(String switchAddress);

    List<LogicalPort> getSwitchLogicalPorts(String switchAddress);

    LogicalPortDto createLogicalPort(String switchAddress, LogicalPortDto payload);

    LogicalPort getSwitchLogicalPortConfig(String switchAddress, Integer logicalPortNumber);

    GrpcDeleteOperationResponse deleteSwitchLogicalPort(String switchAddress, Integer logicalPortNumber);

    EnableLogMessagesResponse enableLogMessagesOnSwitch(String switchAddress, LogMessagesDto payload);

    EnableLogMessagesResponse enableLogOfErrorsOnSwitch(String switchAddress, LogOferrorsDto payload);

    RemoteLogServer getRemoteLogServerForSwitch(String switchAddress);

    RemoteLogServerDto setRemoteLogServerForSwitch(String switchAddress, RemoteLogServerDto payload);

    GrpcDeleteOperationResponse deleteRemoteLogServerForSwitch(String switchAddress);

    // TODO(andriidovhan) implement setPortConfiguration when the issue is fixed
    //    PortConfigDto setPortConfiguration(String switchAddress, String port_number, PortConfigDto payload);

    LicenseResponse setLicenseForSwitch(String switchAddress, LicenseDto payload);
}
