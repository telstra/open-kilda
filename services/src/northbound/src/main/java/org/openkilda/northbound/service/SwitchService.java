package org.openkilda.northbound.service;

import org.openkilda.northbound.dto.SwitchDto;

import java.util.List;

public interface SwitchService extends BasicService {

    List<SwitchDto> getSwitches();

}
