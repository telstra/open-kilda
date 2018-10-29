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

package org.openkilda.messaging.command.system;

import org.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(Include.NON_NULL)
public class FeatureToggleRequest extends CommandData {

	@JsonProperty(value = "sync_rules")
	private Boolean syncRulesEnabled;

	@JsonProperty(value = "reroute_on_isl_discovery")
	private Boolean rerouteOnIslDiscoveryEnabled;

	@JsonProperty("create_flow")
	private Boolean createFlowEnabled;

	@JsonProperty("update_flow")
	private Boolean updateFlowEnabled;

	@JsonProperty("delete_flow")
	private Boolean deleteFlowEnabled;

	@JsonProperty("push_flow")
	private Boolean pushFlowEnabled;

	@JsonProperty("unpush_flow")
	private Boolean unpushFlowEnabled;

	@JsonProperty(value = "child_correlation_id")
	private String childCorrelationId;

	public FeatureToggleRequest(@JsonProperty(value = "sync_rules") Boolean syncRulesEnabled,
			@JsonProperty(value = "reroute_on_isl_discovery") Boolean rerouteOnIslDiscoveryEnabled,
			@JsonProperty("create_flow") Boolean createFlowEnabled,
			@JsonProperty("update_flow") Boolean updateFlowEnabled,
			@JsonProperty("delete_flow") Boolean deleteFlowEnabled,
			@JsonProperty("push_flow") Boolean pushFlowEnabled,
			@JsonProperty("unpush_flow") Boolean unpushFlowEnabled,
			@JsonProperty(value = "child_correlation_id") String childCorrelationId) {
		this.syncRulesEnabled = syncRulesEnabled;
		this.rerouteOnIslDiscoveryEnabled = rerouteOnIslDiscoveryEnabled;
		this.createFlowEnabled = createFlowEnabled;
		this.updateFlowEnabled = updateFlowEnabled;
		this.deleteFlowEnabled = deleteFlowEnabled;
		this.pushFlowEnabled = pushFlowEnabled;
		this.unpushFlowEnabled = unpushFlowEnabled;
		this.childCorrelationId = childCorrelationId;
	}
}
