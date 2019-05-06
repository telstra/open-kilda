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

package org.openkilda.northbound.controller.v1;

import org.openkilda.messaging.model.system.KildaConfigurationDto;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.service.KildaConfigurationService;

import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for toggle existed feature in kilda without having to re-deploy code.
 */
@RestController
@RequestMapping("/v1/config")
@PropertySource("classpath:northbound.properties")
public class KildaConfigurationController extends BaseController {

    @Autowired
    private KildaConfigurationService kildaConfigurationService;

    @GetMapping
    @ApiOperation(value = "Get kilda configuration", response = KildaConfigurationDto.class)
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<KildaConfigurationDto> getKildaConfiguration() {
        return kildaConfigurationService.getKildaConfiguration();
    }

    @PatchMapping
    @ApiOperation(value = "Update kilda configuration")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<KildaConfigurationDto> updateKildaConfiguration(@RequestBody KildaConfigurationDto dto) {
        return kildaConfigurationService.updateKildaConfiguration(dto);
    }
}
