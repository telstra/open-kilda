package org.openkilda.northbound.converter;

import org.mapstruct.Mapper;
import org.openkilda.messaging.command.system.FeatureToggleRequest;
import org.openkilda.messaging.info.system.FeatureTogglesResponse;
import org.openkilda.messaging.payload.FeatureTogglePayload;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface FeatureTogglesMapper {

    default FeatureTogglePayload toDto(FeatureTogglesResponse response) {
        return new FeatureTogglePayload(response.isSyncRulesEnabled(), response.isReflowOnSwitchActivationEnabled());
    }

    //todo: replace with new correlation_id feature
    default FeatureToggleRequest toRequest(FeatureTogglePayload request) {
        return toRequest(request, UUID.randomUUID().toString());
    }

    default FeatureToggleRequest toRequest(FeatureTogglePayload request, String correlationId) {
        return new FeatureToggleRequest(request.getSyncRulesEnabled(), request.getReflowOnSwitchActivationEnabled(),
                correlationId);
    }

}
