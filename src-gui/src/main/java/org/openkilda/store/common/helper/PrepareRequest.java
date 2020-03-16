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

package org.openkilda.store.common.helper;

import org.openkilda.store.common.model.ApiRequestDto;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Map.Entry;

@Component
public class PrepareRequest {

    /**
     * Preprocess api request.
     *
     * @param apiRequestDto the api request dto
     * @param params the params
     */
    public void preprocessApiRequest(final ApiRequestDto apiRequestDto, final Map<String, String> params) {
        for (Entry<String, String> entry : params.entrySet()) {
            apiRequestDto.setUrl(apiRequestDto.getUrl().replaceAll(entry.getKey(), entry.getValue()));
            apiRequestDto.setHeader(apiRequestDto.getHeader().replaceAll(entry.getKey(), entry.getValue()));
            apiRequestDto.setPayload(apiRequestDto.getPayload().replaceAll(entry.getKey(), entry.getValue()));
        }
    }
}
