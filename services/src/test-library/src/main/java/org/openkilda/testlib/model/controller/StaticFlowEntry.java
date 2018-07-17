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

package org.openkilda.testlib.model.controller;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/*
 * The list of field is not complete. Only mandatory and used in our application fields are defined.
 * */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StaticFlowEntry extends AbstractStaticEntry {
    // Flow properties
    @JsonProperty("cookie")
    private String cookie = "0";

    @JsonProperty("cookie_mask")
    private String cookieMask = "0";

    @JsonProperty("in_port")
    private String inPort;

    // Match fields
    @JsonProperty("eth_type")
    private Integer ethType;

    @JsonProperty("eth_src")
    private String ethSource;

    @JsonProperty("eth_dst")
    private String ethDest;

    @JsonProperty("instruction_apply_actions")
    private String action;

    @JsonCreator
    public StaticFlowEntry(
            @JsonProperty("name") String name,
            @JsonProperty("switch") String switchDpid,
            @JsonProperty("cookie") String cookie,
            @JsonProperty("cookie_mask") String cookieMask,
            @JsonProperty("in_port") String inPort,
            @JsonProperty("eth_type") Integer ethType,
            @JsonProperty("eth_src") String ethSource,
            @JsonProperty("eth_dst") String ethDest,
            @JsonProperty("instruction_apply_actions") String action) {
        super(name, switchDpid);

        this.cookie = cookie;
        this.cookieMask = cookieMask;
        this.inPort = inPort;
        this.ethType = ethType;
        this.ethSource = ethSource;
        this.ethDest = ethDest;
        this.action = action;
    }

    public StaticFlowEntry(String name, String switchDpid) {
        super(name, switchDpid);
    }

    public StaticFlowEntry withCookie(Long cookie) {
        this.cookie = String.format("0x%x", cookie);
        return this;
    }

    public StaticFlowEntry withCookieMask(Long cookieMask) {
        this.cookieMask = String.format("0x%x", cookieMask);
        return this;
    }

    public StaticFlowEntry withInPort(String inPort) {
        this.inPort = inPort;
        return this;
    }

    public StaticFlowEntry withEthType(Integer ethType) {
        this.ethType = ethType;
        return this;
    }

    public StaticFlowEntry withEthSource(String ethSource) {
        this.ethSource = ethSource;
        return this;
    }

    public StaticFlowEntry withEthDest(String ethDest) {
        this.ethDest = ethDest;
        return this;
    }

    public StaticFlowEntry withAction(String action) {
        this.action = action;
        return this;
    }
}
