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

package org.openkilda.floodlight.pathverification;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Configuration
public interface PathVerificationServiceConfig {
    @Key("isl_bandwidth_quotient")
    @Default("1.0")
    @Min(0)
    @Max(1)
    double getIslBandwidthQuotient();

    @Key("hmac256-secret")
    @NotBlank
    String getHmac256Secret();

    @Key("verification-bcast-packet-dst")
    @Default("00:26:E1:FF:FF:FF")
    String getVerificationBcastPacketDst();
}
