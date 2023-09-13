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

package org.openkilda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * The Class Filter.
 *
 * @author sumitpal.singh
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"tagk", "group_by", "type", "filter"})
public class Filter implements Serializable {

    private static final long serialVersionUID = -300223703794502712L;

    @JsonProperty("tagk")
    private String tagk;

    @JsonProperty("group_by")
    private boolean groupBy;

    @JsonProperty("type")
    private String type;

    @JsonProperty("filter")
    private String filter;

    @Override
    public String toString() {
        return "Filter [tagk=" + tagk + ", groupBy=" + groupBy + ", type=" + type + ", filter="
                + filter + "]";
    }

}
