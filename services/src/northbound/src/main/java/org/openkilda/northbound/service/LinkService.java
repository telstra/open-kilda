package org.openkilda.northbound.service;

import org.openkilda.northbound.dto.LinksDto;

import java.util.List;

public interface LinkService extends BasicService {

    List<LinksDto> getLinks();

    List<LinksDto> getLinksBySwitch(String switchId);

}
