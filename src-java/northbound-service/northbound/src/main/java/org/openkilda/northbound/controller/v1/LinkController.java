/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.controller.v1;

import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.payload.flow.FlowResponsePayload;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.controller.BaseLinkController;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.v1.links.LinkDto;
import org.openkilda.northbound.dto.v1.links.LinkEnableBfdDto;
import org.openkilda.northbound.dto.v1.links.LinkMaxBandwidthDto;
import org.openkilda.northbound.dto.v1.links.LinkMaxBandwidthRequest;
import org.openkilda.northbound.dto.v1.links.LinkParametersDto;
import org.openkilda.northbound.dto.v1.links.LinkPropsDto;
import org.openkilda.northbound.dto.v1.links.LinkUnderMaintenanceDto;
import org.openkilda.northbound.service.LinkService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for links.
 */
@RestController
@RequestMapping("/v1")
public class LinkController extends BaseLinkController {
    @Autowired
    private LinkService linkService;

    /**
     * Get all available links.
     *
     * @return list of links.
     */
    @GetMapping(path = "/links")
    @Operation(summary = "Get all links, based on arguments.")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LinkDto.class))))
    public CompletableFuture<List<LinkDto>> getLinks(
            @RequestParam(value = "src_switch", required = false) SwitchId srcSwitch,
            @RequestParam(value = "src_port", required = false) Integer srcPort,
            @RequestParam(value = "dst_switch", required = false) SwitchId dstSwitch,
            @RequestParam(value = "dst_port", required = false) Integer dstPort) {
        return linkService.getLinks(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Delete link.
     *
     * @param linkParameters properties to find a link for delete.
     * @return deleted link.
     */
    @DeleteMapping(path = "/links")
    @Operation(summary = "Delete link.")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LinkDto.class))))
    public CompletableFuture<List<LinkDto>> deleteLink(
            @Parameter(description = "default: false. True value means that all link checks (link is inactive, "
                    + "there is no flow with this link) will be ignored.")
            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force,
            @RequestBody LinkParametersDto linkParameters) {
        return linkService.deleteLink(linkParameters, force);
    }

    /**
     * Get link properties from the static link properties table.
     *
     * @return list of link properties.
     */
    @GetMapping(path = "/link/props")
    @Operation(summary = "Get all link properties (static), based on arguments.")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LinkPropsDto.class))))
    public CompletableFuture<List<LinkPropsDto>> getLinkProps(
            @RequestParam(value = "src_switch", required = false) SwitchId srcSwitch,
            @RequestParam(value = "src_port", required = false) Integer srcPort,
            @RequestParam(value = "dst_switch", required = false) SwitchId dstSwitch,
            @RequestParam(value = "dst_port", required = false) Integer dstPort) {
        return linkService.getLinkProps(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Create/Update link properties in the static link properties table.
     *
     * @param keysAndProps if null, get all link props. Otherwise, the link props that much the primary keys.
     * @return result of the processing.
     */
    @PutMapping(path = "/link/props")
    @Operation(summary = "Create/Update link properties")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = BatchResults.class)))
    public CompletableFuture<BatchResults> putLinkProps(@RequestBody List<LinkPropsDto> keysAndProps) {
        return linkService.setLinkProps(keysAndProps);
    }

    /**
     * Delete link properties from the static link properties table.
     *
     * @param keysAndProps if null, get all link props. Otherwise, the link props that much the primary keys.
     * @return result of the processing.
     */
    @DeleteMapping(path = "/link/props")
    @Operation(summary = "Delete link properties (static), based on arguments.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = BatchResults.class)))
    public CompletableFuture<BatchResults> delLinkProps(@RequestBody List<LinkPropsDto> keysAndProps) {
        return linkService.delLinkProps(keysAndProps);
    }

    /**
     * Get all flows for a particular link.
     *
     * @return list of flows for a particular link.
     */
    @GetMapping(path = "/links/flows", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @Operation(summary = "Get all flows for a particular link, based on arguments.")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FlowResponsePayload.class))))
    public CompletableFuture<List<FlowResponsePayload>> getFlowsForLink(@RequestParam(value = "src_switch")
                                                                        SwitchId srcSwitch,
                                                                        @RequestParam(value = "src_port")
                                                                        Integer srcPort,
                                                                        @RequestParam(value = "dst_switch")
                                                                        SwitchId dstSwitch,
                                                                        @RequestParam(value = "dst_port")
                                                                        Integer dstPort) {
        return linkService.getFlowsForLink(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Reroute all flows for a particular link.
     *
     * @return list of flow ids which was sent to reroute.
     */
    @PatchMapping(path = "/links/flows/reroute", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @Operation(summary = "Reroute all flows for a particular link, based on arguments.")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class))))
    public CompletableFuture<List<String>> rerouteFlowsForLink(@RequestParam(value = "src_switch") SwitchId srcSwitch,
                                                               @RequestParam(value = "src_port") Integer srcPort,
                                                               @RequestParam(value = "dst_switch") SwitchId dstSwitch,
                                                               @RequestParam(value = "dst_port") Integer dstPort) {
        return linkService.rerouteFlowsForLink(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Update "Under maintenance" flag in the link.
     *
     * @return updated link.
     */
    @PatchMapping(path = "/links/under-maintenance", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @Operation(summary = "Update \"Under maintenance\" flag for the link.")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LinkDto.class))))
    public CompletableFuture<List<LinkDto>> updateLinkUnderMaintenance(@RequestBody LinkUnderMaintenanceDto link) {
        return linkService.updateLinkUnderMaintenance(link);
    }

    @PatchMapping(path = "/links/bandwidth")
    @Operation(summary = "Update maximum bandwidth on the link")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = LinkMaxBandwidthDto.class)))
    public CompletableFuture<LinkMaxBandwidthDto> updateLinkParams(
            @RequestParam(value = "src_switch") SwitchId srcSwitch,
            @RequestParam(value = "src_port") Integer srcPort,
            @RequestParam(value = "dst_switch") SwitchId dstSwitch,
            @RequestParam(value = "dst_port") Integer dstPort,
            @RequestBody LinkMaxBandwidthRequest linkMaxBandwidth) {
        return linkService.updateLinkBandwidth(srcSwitch, srcPort, dstSwitch, dstPort, linkMaxBandwidth);
    }

    /**
     * Update "enable bfd" flag in the link.
     *
     * @return updated link.
     */
    @PatchMapping(path = "/links/enable-bfd", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @Operation(summary = "Update \"enable bfd\" flag for the link.")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LinkDto.class))))
    public CompletableFuture<List<LinkDto>> updateLinkEnableBfd(@RequestBody LinkEnableBfdDto link) {
        NetworkEndpoint source = makeSourceEndpoint(new SwitchId(link.getSrcSwitch()), link.getSrcPort());
        NetworkEndpoint destination = makeDestinationEndpoint(new SwitchId(link.getDstSwitch()), link.getDstPort());
        return linkService.writeBfdProperties(source, destination, link.isEnableBfd());
    }
}
