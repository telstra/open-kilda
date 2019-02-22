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

package org.openkilda.grpc.speaker.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LicenseDto {

    @JsonProperty("license_file_name")
    private String licenseFileName;

    @JsonProperty("license_data")
    private String licenseData;

    @JsonCreator
    public LicenseDto(@JsonProperty("license_file_name") String licenseFileName,
                      @JsonProperty("license_data") String licenseData) {
        if (licenseFileName == null && licenseData == null) {
            throw new IllegalArgumentException("One of fields must not be null");
        }
        this.licenseFileName = licenseFileName;
        this.licenseData = licenseData;
    }
}
