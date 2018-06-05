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

package org.openkilda.messaging.te.response;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.LinkProps;
import org.openkilda.messaging.te.request.LinkPropsRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class LinkPropsResponse extends InfoData {
    @JsonProperty("request")
    LinkPropsRequest request;

    @JsonProperty("link_props")
    LinkProps linkProps;

    @JsonProperty("error")
    private String error;

    @JsonCreator
    public LinkPropsResponse(
            @JsonProperty("request") LinkPropsRequest request,
            @JsonProperty("link_props") LinkProps linkProps,
            @JsonProperty("error") String error) {
        this.request = request;
        this.linkProps = linkProps;
        this.error = error;
    }

    @JsonIgnore
    public boolean isSuccess() {
        return error == null;
    }
}
