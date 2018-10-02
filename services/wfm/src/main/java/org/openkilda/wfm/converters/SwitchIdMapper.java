package org.openkilda.wfm.converters;


import org.mapstruct.Mapping;
import org.openkilda.messaging.model.SwitchId;
import org.mapstruct.Mapper;

@Mapper
public interface SwitchIdMapper {
    default SwitchId toMessaging(org.openkilda.model.SwitchId switchId){
        return new SwitchId(switchId.getId());
    }

    default org.openkilda.model.SwitchId toDb(SwitchId switchId) {
        return new org.openkilda.model.SwitchId(switchId.toLong());
    }
}
