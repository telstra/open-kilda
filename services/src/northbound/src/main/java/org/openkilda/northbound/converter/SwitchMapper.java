package org.openkilda.northbound.converter;

import org.mapstruct.Mapper;
import org.openkilda.client.response.switches.SyncRulesOutput;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.SyncRulesResponse;
import org.openkilda.northbound.dto.SwitchDto;

@Mapper(componentModel = "spring")
public interface SwitchMapper {

    SwitchDto toSwitchDto(SwitchInfoData data);

    default SyncRulesOutput toSuncRulesOutput(SyncRulesResponse response) {
        return new SyncRulesOutput(response.getAddedRules(), response.getProperRules(), response.getNotDeleted());
    }

}
