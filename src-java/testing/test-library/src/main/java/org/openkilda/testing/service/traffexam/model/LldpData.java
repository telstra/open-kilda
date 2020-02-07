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

package org.openkilda.testing.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.github.javafaker.Faker;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class LldpData {
    @NonNull
    String macAddress;
    @NonNull
    String chassisId;
    @NonNull
    String portNumber;
    @NonNull
    int timeToLive;
    String portDescription;
    String systemName;
    String systemDescription;
    String systemCapabilities;
    String managementAddress;

    /**
     * Build LldpData object with random values.
     */
    public static LldpData buildRandom() {
        Faker faker = new Faker();
        return LldpData.builder()
                .macAddress(faker.internet().macAddress("10")) //control first bit in the left-most byte
                .chassisId(faker.internet().macAddress())
                .portNumber(String.valueOf(faker.number().numberBetween(1, 65535)))
                .timeToLive(faker.number().numberBetween(10, 300))
                .build();
    }
}
