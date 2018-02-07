package org.openkilda.northbound.service;

import org.openkilda.northbound.dto.LinkPropsDto;
import org.openkilda.northbound.dto.LinksDto;

import java.util.List;
import java.util.Map;

public interface LinkService extends BasicService {

    /** @return the links (ISLs) that have been discovered */
    List<LinksDto> getLinks();

    /**
     * These results are not related to the ISL links per se .. they are based on any link
     * properties that have been uploaded through setLinkProps.
     *
     * @param keys the primary keys are used if set, and the props are ignored. If nothing is set, all
     *             link properties rows are returned.
     * @return one or more link properties from the static link_props table
     * */
    List<LinkPropsDto> getLinkProps(LinkPropsDto keys);

    /**
     * All linkPropsList link properties will be created/updated, and pushed to ISL links if they exit.
     *
     * @param linkPropsList the list of link properties to create / update
     * @return the number of successes, failures, and any failure messages
     */
    LinkPropsResult setLinkProps(List<LinkPropsDto> linkPropsList);

    /**
     * All linkPropsList link properties will be deleted, and deleted from ISL links if they exist.
     *
     * @param linkPropsList the list of link properties to delete
     * @return the number of successes (rows affected), failures, and any failure messages
     */
    LinkPropsResult delLinkProps(List<LinkPropsDto> linkPropsList);
}
