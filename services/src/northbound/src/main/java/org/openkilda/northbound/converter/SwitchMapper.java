package org.openkilda.northbound.converter;

import org.mapstruct.Mapper;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.northbound.dto.SwitchDto;

@Mapper(componentModel = "spring")
public interface SwitchMapper {

    SwitchDto toSwitchDto(SwitchInfoData data);

}
