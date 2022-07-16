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
import org.openkilda.messaging.error.GrpcMessageError;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.RemoteLogServer;
import org.openkilda.messaging.model.grpc.SwitchInfoStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(value = "/noviflow", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
@ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Operation is successful"),
        @ApiResponse(responseCode = "400", description = "Invalid input data",
                content = @Content(schema = @Schema(implementation = GrpcMessageError.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
                content = @Content(schema = @Schema(implementation = GrpcMessageError.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden",
                content = @Content(schema = @Schema(implementation = GrpcMessageError.class))),
        @ApiResponse(responseCode = "404", description = "Not found",
                content = @Content(schema = @Schema(implementation = GrpcMessageError.class))),
        @ApiResponse(responseCode = "500", description = "General error",
                content = @Content(schema = @Schema(implementation = GrpcMessageError.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                content = @Content(schema = @Schema(implementation = GrpcMessageError.class)))})
public class NoviflowController {

    @Autowired
    private GrpcSenderService grpcService;

    @GetMapping(path = "/{switch_address}/status")
    @Operation(summary = "Get switch status")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SwitchInfoStatus.class)))
    public CompletableFuture<SwitchInfoStatus> getSwitchStatus(@PathVariable("switch_address") String switchAddress) {
        return grpcService.getSwitchStatus(switchAddress);
    }

    @GetMapping(path = "/{switch_address}/logicalports")
    @Operation(summary = "Get switch logical ports")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LogicalPort.class))))
    public CompletableFuture<List<LogicalPort>> getSwitchLogicalPorts(
            @PathVariable("switch_address") String switchAddress) {
        return grpcService.dumpLogicalPorts(switchAddress);
    }

    @PutMapping(path = "/{switch_address}/logicalports")
    @Operation(summary = "Create a logical port")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = LogicalPortDto.class)))
    public CompletableFuture<LogicalPort> createLogicalPort(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody LogicalPortDto logicalPortDto) {
        return grpcService.createOrUpdateLogicalPort(switchAddress, logicalPortDto);
    }

    @GetMapping(path = "/{switch_address}/logicalports/{logical_port_number}")
    @Operation(summary = "Get switch logical port configuration")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = LogicalPort.class)))
    public CompletableFuture<LogicalPort> getSwitchLogicalPortConfig(
            @PathVariable("switch_address") String switchAddress,
            @PathVariable("logical_port_number") Integer logicalPortNumber) {
        return grpcService.showConfigLogicalPort(switchAddress, logicalPortNumber);
    }

    @DeleteMapping(path = "/{switch_address}/logicalports/{logical_port_number}")
    @Operation(summary = "Delete switch logical port")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = GrpcDeleteOperationResponse.class)))
    public CompletableFuture<GrpcDeleteOperationResponse> deleteSwitchLogicalPort(
            @PathVariable("switch_address") String switchAddress,
            @PathVariable("logical_port_number") Integer logicalPortNumber) {
        return grpcService.deleteConfigLogicalPort(switchAddress, logicalPortNumber);
    }

    @PutMapping(path = "/{switch_address}/logmessages")
    @Operation(summary = "Set configuration for logging \"messages\" on the switch")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = EnableLogMessagesResponse.class)))
    public CompletableFuture<EnableLogMessagesResponse> enableLogMessages(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody LogMessagesDto logMessagesDto) {
        return grpcService.enableLogMessages(switchAddress, logMessagesDto);
    }

    @PutMapping(path = "/{switch_address}/logoferrors")
    @Operation(summary = "Set configuration for logging OF Errors on the switch")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = EnableLogMessagesResponse.class)))
    public CompletableFuture<EnableLogMessagesResponse> enableLogOfErrors(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody LogOferrorsDto ofErrorsDto) {
        return grpcService.enableLogOfError(switchAddress, ofErrorsDto);
    }

    @GetMapping(path = "/{switch_address}/remotelogserver")
    @Operation(summary = "Get a remote log server configuration for the switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = RemoteLogServer.class)))
    public CompletableFuture<RemoteLogServer> showConfigRemoteLogServer(
            @PathVariable("switch_address") String switchAddress) {
        return grpcService.showConfigRemoteLogServer(switchAddress);
    }

    @PutMapping(path = "/{switch_address}/remotelogserver")
    @Operation(summary = "Set a remote log server configuration for switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = RemoteLogServer.class)))
    public CompletableFuture<RemoteLogServer> setConfigRemoteLogServer(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody RemoteLogServerDto remoteLogServerDto) {
        return grpcService.setConfigRemoteLogServer(switchAddress, remoteLogServerDto);
    }

    @DeleteMapping(path = "/{switch_address}/remotelogserver")
    @Operation(summary = "Delete a remote log server configuration for switch")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = GrpcDeleteOperationResponse.class)))
    public CompletableFuture<GrpcDeleteOperationResponse> deleteConfigRemoteLogServer(
            @PathVariable("switch_address") String switchAddress) {
        return grpcService.deleteConfigRemoteLogServer(switchAddress);
    }

    @PutMapping(path = "/{switch_address}/{port_number}/config")
    @Operation(summary = "Set port configuration")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = PortConfigSetupResponse.class)))
    public CompletableFuture<PortConfigSetupResponse> setPortConfig(
            @PathVariable("switch_address") String switchAddress,
            @PathVariable("port_number") Integer portNumber,
            @RequestBody PortConfigDto portConfigDto) {
        return grpcService.setPortConfig(switchAddress, portNumber, portConfigDto);
    }

    @PutMapping(path = "{switch_address}/license")
    @Operation(summary = "Set a license configuration for switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = LicenseResponse.class)))
    public CompletableFuture<LicenseResponse> setConfigLicense(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody LicenseDto licenseDto) {
        return grpcService.setConfigLicense(switchAddress, licenseDto);
    }

    @GetMapping(path = "{switch_address}/packet-in-out-stats")
    @Operation(summary = "Get packet in out stats for switch")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = PacketInOutStatsResponse.class)))
    public CompletableFuture<PacketInOutStatsResponse> packetInOutStats(
            @PathVariable("switch_address") String switchAddress) {
        return grpcService.getPacketInOutStats(switchAddress);
    }
}
