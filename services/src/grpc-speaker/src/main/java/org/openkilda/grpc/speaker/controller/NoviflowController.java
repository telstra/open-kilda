package org.openkilda.grpc.speaker.controller;

import org.openkilda.grpc.speaker.model.EnableLogMessagesResponse;
import org.openkilda.grpc.speaker.model.GrpcDeleteOperationResponse;
import org.openkilda.grpc.speaker.model.LogMessagesDto;
import org.openkilda.grpc.speaker.model.LogOferrorsDto;
import org.openkilda.grpc.speaker.model.LogicalPortDto;
import org.openkilda.grpc.speaker.model.PortConfigDto;
import org.openkilda.grpc.speaker.model.PortConfigSetupResponse;
import org.openkilda.grpc.speaker.model.RemoteLogServerDto;
import org.openkilda.grpc.speaker.service.GrpcSenderService;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.RemoteLogServer;
import org.openkilda.messaging.model.grpc.SwitchInfoStatus;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
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
@RequestMapping(value = "/noviflow", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
@PropertySource("classpath:grpc-speaker.properties")
@Api
@ApiResponses(value = {
        @ApiResponse(code = 200, message = "Operation is successful"),
        @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
        @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
        @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
        @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
        @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
        @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})

public class NoviflowController {

    @Autowired
    private GrpcSenderService grpcService;

    @ApiOperation(value = "Get switch status", response = SwitchInfoStatus.class)
    @GetMapping(path = "/{switch_address}/status")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchInfoStatus> getSwitchStatus(@PathVariable("switch_address") String switchAddress) {
        return grpcService.getSwitchStatus(switchAddress);
    }

    @ApiOperation(value = "Get switch logical ports", response = LogicalPort.class, responseContainer = "List")
    @GetMapping(path = "/{switch_address}/logicalports")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<LogicalPort>> getSwitchLogicalPorts(
            @PathVariable("switch_address") String switchAddress) {
        return grpcService.dumpLogicalPorts(switchAddress);
    }

    @ApiOperation(value = "Create logical port", response = LogicalPortDto.class)
    @PutMapping(path = "/{switch_address}/logicalports")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<LogicalPort> createLogicalPort(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody LogicalPortDto logicalPortDto) {
        return grpcService.createLogicalPort(switchAddress, logicalPortDto);
    }

    @ApiOperation(value = "Get switch logical port config", response = LogicalPort.class)
    @GetMapping(path = "/{switch_address}/logicalports/{logical_port_number}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<LogicalPort> getSwitchLogicalPortConfig(
            @PathVariable("switch_address") String switchAddress,
            @PathVariable("logical_port_number") Integer logicalPortNumber) {
        return grpcService.showConfigLogicalPort(switchAddress, logicalPortNumber);
    }

    @ApiOperation(value = "Delete switch logical port", response = GrpcDeleteOperationResponse.class)
    @DeleteMapping(path = "/{switch_address}/logicalports/{logical_port_number}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<GrpcDeleteOperationResponse> deleteSwitchLogicalPort(
            @PathVariable("switch_address") String switchAddress,
            @PathVariable("logical_port_number") Integer logicalPortNumber) {
        return grpcService.deleteConfigLogicalPort(switchAddress, logicalPortNumber);
    }

    @ApiOperation(value = "Enable log messages on switch", response = EnableLogMessagesResponse.class)
    @PutMapping(path = "/{switch_address}/logmessages")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<EnableLogMessagesResponse> enableLogMessages(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody LogMessagesDto logMessagesDto) {
        return grpcService.enableLogMessages(switchAddress, logMessagesDto);
    }

    @ApiOperation(value = "Enable log OF errors on switch", response = EnableLogMessagesResponse.class)
    @PutMapping(path = "/{switch_address}/logoferrors")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<EnableLogMessagesResponse> enableLogOferrors(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody LogOferrorsDto logOferrors) {
        return grpcService.enableLogOferror(switchAddress, logOferrors);
    }

    @ApiOperation(value = "Get a remote log server for switch", response = RemoteLogServer.class)
    @GetMapping(path = "/{switch_address}/remotelogserver")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<RemoteLogServer> showConfigRemoteLogServer(
            @PathVariable("switch_address") String switchAddress) {
        return grpcService.showConfigRemoteLogServer(switchAddress);
    }

    @ApiOperation(value = "Set a remote log server for switch", response = RemoteLogServer.class)
    @PutMapping(path = "/{switch_address}/remotelogserver")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<RemoteLogServer> setConfigRemoteLogServer(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody RemoteLogServerDto remoteLogServerDto) {
        return grpcService.setConfigRemoteLogServer(switchAddress, remoteLogServerDto);
    }

    @ApiOperation(value = "Delete remote log server for switch", response = GrpcDeleteOperationResponse.class)
    @DeleteMapping(path = "/{switch_address}/remotelogserver")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<GrpcDeleteOperationResponse> deleteConfigRemoteLogServer(
            @PathVariable("switch_address") String switchAddress,
            @RequestBody RemoteLogServerDto remoteLogServerDto) {
        return grpcService.deleteConfigRemoteLogServer(switchAddress, remoteLogServerDto);
    }

    @ApiOperation(value = "Set port configuration", response = PortConfigSetupResponse.class)
    @PutMapping(path = "/{switch_address}/{port_number}/config")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<PortConfigSetupResponse> setPortConfig(
            @PathVariable("switch_address") String switchAddress,
            @PathVariable("port_number") Integer portNumber,
            @RequestBody PortConfigDto portConfigDto) {
        return grpcService.setPortConfig(switchAddress, portNumber, portConfigDto);
    }
}
