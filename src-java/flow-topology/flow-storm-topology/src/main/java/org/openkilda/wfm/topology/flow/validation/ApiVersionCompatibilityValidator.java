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

package org.openkilda.wfm.topology.flow.validation;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;

public final class ApiVersionCompatibilityValidator {
    public static final ApiVersionCompatibilityValidator INSTANCE = new ApiVersionCompatibilityValidator();

    /**
     * Check API version compatibility and raise {@code FlowValidationException} in case of incompatibility.
     */
    public void enforce(Flow flow) throws FlowValidationException {
        if (! validateCompatibility(flow)) {
            throw new FlowValidationException(format(
                    "The flow '%s' use double VLAN tagged endpoint(s), API v1 is not capable to handle such flow. "
                            + "Use API v2.", flow.getFlowId()), ErrorType.DATA_INVALID);
        }
    }

    /**
     * Check API version compatibility.
     */
    public boolean validateCompatibility(Flow flow) {
        return ! (
                FlowEndpoint.isVlanIdSet(flow.getSrcInnerVlan())
                        || FlowEndpoint.isVlanIdSet(flow.getDestInnerVlan()));
    }

    private ApiVersionCompatibilityValidator() {}
}
