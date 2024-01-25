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

package org.openkilda.grpc.speaker.controller;


import org.openkilda.grpc.speaker.model.EnableLogMessagesResponse;
import org.openkilda.grpc.speaker.model.GrpcDeleteOperationResponse;
import org.openkilda.grpc.speaker.model.LicenseDto;
import org.openkilda.grpc.speaker.model.LicenseResponse;
import org.openkilda.grpc.speaker.model.LogMessagesDto;
import org.openkilda.grpc.speaker.model.LogOferrorsDto;
import org.openkilda.grpc.speaker.model.LogicalPortDto;
import org.openkilda.grpc.speaker.model.PacketInOutStatsResponse;
import org.openkilda.grpc.speaker.model.PortConfigDto;
import org.openkilda.grpc.speaker.model.PortConfigSetupResponse;
import org.openkilda.grpc.speaker.model.RemoteLogServerDto;
import org.openkilda.grpc.speaker.service.GrpcSenderService;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.RemoteLogServer;
import org.openkilda.messaging.model.grpc.SwitchInfoStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(value = "/noviflow", produces = MediaType.APPLICATION_JSON_VALUE)
@PropertySource("classpath:grpc-service.properties")
@ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Operation is successful"),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Not found"),
        @ApiResponse(responseCode = "500", description = "General error"),
        @ApiResponse(responseCode = "503", description = "Service unavailable")})
public class NoviflowController {

    @Autowired
    private GrpcSenderService grpcService;

    @Operation(summary = "Get switch status")
    @GetMapping(path = "/{switch_address}/status")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchInfoStatus> getSwitchStatus(@PathVariable("switch_address") String switchAddress) {
        return grpcService.getSwitchStatus(switchAddress);
    }

    @Operation(summary = "Get switch logical ports")
    @GetMapping(path = "/{switch_address}/logicalports")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<LogicalPort>> getSwitchLogicalPorts(
            @PathVariable("switch_address") String switchAddress) {
        return grpcService.dumpLogicalPorts(switchAddress);
    }

    @Operation(summary = "Create a logical port")
    @PutMapping(path = "/{switch_address}/logicalports")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<LogicalPort> createLogicalPort(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody LogicalPortDto logicalPortDto) {
        return grpcService.createOrUpdateLogicalPort(switchAddress, logicalPortDto);
    }

    @Operation(summary = "Get switch logical port configuration")
    @GetMapping(path = "/{switch_address}/logicalports/{logical_port_number}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<LogicalPort> getSwitchLogicalPortConfig(
            @PathVariable("switch_address") String switchAddress,
            @PathVariable("logical_port_number") Integer logicalPortNumber) {
        return grpcService.showConfigLogicalPort(switchAddress, logicalPortNumber);
    }

    @Operation(summary = "Delete switch logical port")
    @DeleteMapping(path = "/{switch_address}/logicalports/{logical_port_number}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<GrpcDeleteOperationResponse> deleteSwitchLogicalPort(
            @PathVariable("switch_address") String switchAddress,
            @PathVariable("logical_port_number") Integer logicalPortNumber) {
        return grpcService.deleteConfigLogicalPort(switchAddress, logicalPortNumber);
    }

    @Operation(summary = "Set configuration for logging \"messages\" on the switch")
    @PutMapping(path = "/{switch_address}/logmessages")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<EnableLogMessagesResponse> enableLogMessages(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody LogMessagesDto logMessagesDto) {
        return grpcService.enableLogMessages(switchAddress, logMessagesDto);
    }

    @Operation(summary = "Set configuration for logging OF Errors on the switch")
    @PutMapping(path = "/{switch_address}/logoferrors")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<EnableLogMessagesResponse> enableLogOfErrors(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody LogOferrorsDto ofErrorsDto) {
        return grpcService.enableLogOfError(switchAddress, ofErrorsDto);
    }

    @Operation(summary = "Get a remote log server configuration for the switch")
    @GetMapping(path = "/{switch_address}/remotelogserver")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<RemoteLogServer> showConfigRemoteLogServer(
            @PathVariable("switch_address") String switchAddress) {
        return grpcService.showConfigRemoteLogServer(switchAddress);
    }

    @Operation(summary = "Set a remote log server configuration for switch")
    @PutMapping(path = "/{switch_address}/remotelogserver")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<RemoteLogServer> setConfigRemoteLogServer(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody RemoteLogServerDto remoteLogServerDto) {
        return grpcService.setConfigRemoteLogServer(switchAddress, remoteLogServerDto);
    }

    @Operation(summary = "Delete a remote log server configuration for switch")
    @DeleteMapping(path = "/{switch_address}/remotelogserver")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<GrpcDeleteOperationResponse> deleteConfigRemoteLogServer(
            @PathVariable("switch_address") String switchAddress) {
        return grpcService.deleteConfigRemoteLogServer(switchAddress);
    }

    @Operation(summary = "Set port configuration")
    @PutMapping(path = "/{switch_address}/{port_number}/config")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<PortConfigSetupResponse> setPortConfig(
            @PathVariable("switch_address") String switchAddress,
            @PathVariable("port_number") Integer portNumber,
            @RequestBody PortConfigDto portConfigDto) {
        return grpcService.setPortConfig(switchAddress, portNumber, portConfigDto);
    }

    @Operation(summary = "Set a license configuration for switch")
    @PutMapping(path = "{switch_address}/license")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<LicenseResponse> setConfigLicense(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody LicenseDto licenseDto) {
        return grpcService.setConfigLicense(switchAddress, licenseDto);
    }

    @Operation(summary = "Get packet in out stats for switch")
    @GetMapping(path = "{switch_address}/packet-in-out-stats")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<PacketInOutStatsResponse> packetInOutStats(
            @PathVariable("switch_address") String switchAddress) {
        return grpcService.getPacketInOutStats(switchAddress);
    }
}
