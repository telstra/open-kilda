package org.bitbucket.openkilda.messaging.info;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Defines the data payload of a Message representing a path info.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PathInfoData extends IslInfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     */
    public PathInfoData() {
    }
}
