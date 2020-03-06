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

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.github.javafaker.Faker;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class ArpData {
    @NonNull
    String srcMac;
    @NonNull
    String srcIpv4;

    /**
     * Build ArpData object with random values.
     */
    public static ArpData buildRandom() {
        Faker faker = new Faker();
        return ArpData.builder()
                .srcMac(faker.internet().macAddress("10"))
                .srcIpv4(faker.internet().ipV4Address())
                .build();
    }
}
