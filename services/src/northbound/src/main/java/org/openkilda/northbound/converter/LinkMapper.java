package org.openkilda.northbound.converter;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.northbound.dto.LinksDto;

@Mapper(componentModel = "spring")
public interface LinkMapper {

    LinksDto toLinkDto(IslInfoData data);
}
