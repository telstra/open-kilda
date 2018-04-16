package org.openkilda.northbound.converter;

import org.mapstruct.Mapper;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.SyncRulesResponse;
import org.openkilda.northbound.dto.SwitchDto;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SwitchMapper {

    SwitchDto toSwitchDto(SwitchInfoData data);

    default RulesSyncResult toRulesSyncResult(SyncRulesResponse response) {
        return new RulesSyncResult(response.getMissingRules(), response.getProperRules(),
                response.getExcessRules(), response.getInstalledRules());
    }

    default RulesSyncResult toRulesSyncResult(RulesValidationResult validationResult, List<String> installedRules) {
        return new RulesSyncResult(validationResult.getMissingRules(), validationResult.getProperRules(),
                validationResult.getExcessRules(), installedRules);
    }

    default RulesValidationResult toRulesValidationResult(SyncRulesResponse response) {
        return new RulesValidationResult(response.getMissingRules(), response.getProperRules(),
                response.getExcessRules());
    }
}
