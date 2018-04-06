package org.openkilda.northbound.converter;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.openkilda.messaging.command.system.FeatureToggleRequest;
import org.openkilda.messaging.info.system.FeatureTogglesResponse;
import org.openkilda.messaging.payload.FeatureTogglePayload;

@Mapper(componentModel = "spring")
public interface FeatureTogglesMapper {

	default FeatureTogglePayload toDto(FeatureTogglesResponse response) {
		return new FeatureTogglePayload(response.getSyncRulesEnabled(), response.getReflowOnSwitchActivationEnabled(),
				response.getCreateFlowEnabled(), response.getUpdateFlowEnabled(), response.getDeleteFlowEnabled(),
				response.getPushFlowEnabled(), response.getUnpushFlowEnabled());
	}

	// todo: replace with new correlation_id feature
	default FeatureToggleRequest toRequest(FeatureTogglePayload request) {
		return toRequest(request, UUID.randomUUID().toString());
	}

	default FeatureToggleRequest toRequest(FeatureTogglePayload request, String correlationId) {
		return new FeatureToggleRequest(request.getSyncRulesEnabled(), request.getReflowOnSwitchActivationEnabled(),
				request.getCreateFlowEnabled(), request.getUpdateFlowEnabled(), request.getDeleteFlowEnabled(),
				request.getPushFlowEnabled(), request.getUnpushFlowEnabled(), correlationId);
	}

}
