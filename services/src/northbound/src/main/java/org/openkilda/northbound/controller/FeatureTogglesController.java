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

package org.openkilda.northbound.controller;

import org.openkilda.messaging.payload.FeatureTogglePayload;
import org.openkilda.northbound.service.FeatureTogglesService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for toggle existed feature in kilda without having to re-deploy code.
 */
@RestController
@RequestMapping("/features")
@PropertySource("classpath:northbound.properties")
@Api
public class FeatureTogglesController {

    @Autowired
    private FeatureTogglesService featureTogglesService;

    @ApiOperation(value = "Toggle kilda features")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Operation is successful")
    })
    @RequestMapping(method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)
    public void toggleFeatures(@RequestBody FeatureTogglePayload request) {
        featureTogglesService.toggleFeatures(request);
    }

    @ApiOperation(value = "Get states of feature toggles", response = FeatureTogglePayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Operation is successful")
    })
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public FeatureTogglePayload getFeatureTogglesState() {
        return featureTogglesService.getFeatureTogglesState();
    }
}
