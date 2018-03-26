package org.openkilda.northbound.converter;

import org.mapstruct.Mapper;
import org.openkilda.messaging.command.system.FeatureToggleRequest;
import org.openkilda.messaging.info.system.FeatureTogglesResponse;
import org.openkilda.northbound.dto.FeatureToggleDto;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface FeatureTogglesMapper {

    default FeatureToggleDto toDto(FeatureTogglesResponse response) {
        return new FeatureToggleDto(response.isSyncRulesEnabled(), response.isReflowOnSwitchActivationEnabled());
    }

    //todo: replace with new correlation_id feature
    default FeatureToggleRequest toRequest(FeatureToggleDto request) {
        return toRequest(request, UUID.randomUUID().toString());
    }

    default FeatureToggleRequest toRequest(FeatureToggleDto request, String correlationId) {
        return new FeatureToggleRequest(request.getSyncRulesEnabled(), request.getReflowOnSwitchActivationEnabled(),
                correlationId);
    }

}
