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
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.RemoteLogServer;
import org.openkilda.messaging.model.grpc.SwitchInfoStatus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class GrpcServiceImpl implements GrpcService {
    @Autowired
    @Qualifier("grpcRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public SwitchInfoStatus getSwitchStatus(String switchAddress) {
        return restTemplate.exchange("/api/v1/noviflow/{switch_address}/status", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), SwitchInfoStatus.class, switchAddress).getBody();
    }

    @Override
    public List<LogicalPort> getSwitchLogicalPorts(String switchAddress) {
        LogicalPort[] logicalPorts =
                restTemplate.exchange("/api/v1/noviflow/{switch_address}/logicalports", HttpMethod.GET,
                        new HttpEntity(buildHeadersWithCorrelationId()), LogicalPort[].class, switchAddress).getBody();
        return Arrays.asList(logicalPorts);
    }

    @Override
    public LogicalPortDto createLogicalPort(String switchAddress, LogicalPortDto payload) {
        HttpEntity<LogicalPortDto> httpEntity = new HttpEntity<>(payload, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v1/noviflow/{switch_address}/logicalports", HttpMethod.PUT, httpEntity,
                LogicalPortDto.class, switchAddress).getBody();
    }

    @Override
    public LogicalPort getSwitchLogicalPortConfig(String switchAddress, Integer logicalPortNumber) {
        return restTemplate.exchange(
                "/api/v1/noviflow/{switch_address}/logicalports/{logical_port_number}", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), LogicalPort.class, switchAddress,
                logicalPortNumber).getBody();
    }

    @Override
    public GrpcDeleteOperationResponse deleteSwitchLogicalPort(String switchAddress, Integer logicalPortNumber) {
        return restTemplate.exchange(
                "/api/v1/noviflow/{switch_address}/logicalports/{logical_port_number}", HttpMethod.DELETE,
                new HttpEntity(buildHeadersWithCorrelationId()), GrpcDeleteOperationResponse.class,
                switchAddress, logicalPortNumber).getBody();
    }

    @Override
    public RemoteLogServer getRemoteLogServerForSwitch(String switchAddress) {
        return restTemplate.exchange("/api/v1/noviflow/{switch_address}/remotelogserver", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), RemoteLogServer.class, switchAddress).getBody();
    }

    @Override
    public RemoteLogServerDto setRemoteLogServerForSwitch(String switchAddress, RemoteLogServerDto payload) {
        HttpEntity<RemoteLogServerDto> httpEntity = new HttpEntity<>(payload, buildHeadersWithCorrelationId());
        return restTemplate.exchange(
                "/api/v1/noviflow/{switch_address}/remotelogserver", HttpMethod.PUT, httpEntity,
                RemoteLogServerDto.class, switchAddress).getBody();
    }

    @Override
    public GrpcDeleteOperationResponse deleteRemoteLogServerForSwitch(String switchAddress) {
        return restTemplate.exchange("/api/v1/noviflow/{switch_address}/remotelogserver", HttpMethod.DELETE,
                new HttpEntity<>(buildHeadersWithCorrelationId()), GrpcDeleteOperationResponse.class,
                switchAddress).getBody();
    }

    @Override
    public EnableLogMessagesResponse enableLogMessagesOnSwitch(String switchAddress, LogMessagesDto payload) {
        HttpEntity<Object> httpEntity = new HttpEntity<>(payload, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v1/noviflow/{switch_address}/logmessages", HttpMethod.PUT, httpEntity,
                EnableLogMessagesResponse.class, switchAddress).getBody();
    }

    @Override
    public EnableLogMessagesResponse enableLogOfErrorsOnSwitch(String switchAddress, LogOferrorsDto payload) {
        HttpEntity<LogOferrorsDto> httpEntity = new HttpEntity<>(payload, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v1/noviflow/{switch_address}/logoferrors", HttpMethod.PUT, httpEntity,
                EnableLogMessagesResponse.class, switchAddress).getBody();
    }

    @Override
    public LicenseResponse setLicenseForSwitch(String switchAddress, LicenseDto payload) {
        HttpEntity<Object> httpEntity = new HttpEntity<>(payload, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v1/noviflow/{switch_address}/license", HttpMethod.PUT, httpEntity,
                LicenseResponse.class, switchAddress).getBody();
    }

    private HttpHeaders buildHeadersWithCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));
        return headers;
    }
}
