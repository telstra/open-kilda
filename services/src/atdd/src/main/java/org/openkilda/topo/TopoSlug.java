/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.topo;

/**
 * Topology Slug - used to identify a topology element.
 */
public class TopoSlug {

    public static final String DELIM = ":";
    public static final String EP_DELIM = "->";
    public static final String NULL_SWITCH = "S.0";
    public static final String NULL_PORT = NULL_SWITCH + DELIM + "P.0";
    public static final String NULL_ENDPOINT = NULL_PORT + DELIM + "Q.0";
    public static final String NULL_LINK = "L" + DELIM + NULL_ENDPOINT + EP_DELIM + NULL_ENDPOINT;

    /**
     * Returns a string representation of the switch.
     *
     * @param aSwitch the switch.
     * @return string representation.
     */
    public static final String toString(Switch aSwitch) {
        return "S." + aSwitch.getId();
    }

    /**
     * Returns a string representation of the port.
     *
     * @param port the port.
     * @return string representation.
     */
    public static final String toString(Port port) {
        // NB: mostly a bug to have a null parent .. but allow it for now
        String parentSlug = (port.getParent() != null) ? port.getParent().getSlug() : NULL_SWITCH;
        return parentSlug + DELIM + "P." + port.getId();
    }

    /**
     * Returns a string representation of the port queue.
     *
     * @param portQueue the port queue.
     * @return string representation.
     */
    public static final String toString(PortQueue portQueue) {
        String parentSlug = (portQueue.getParent() != null)
                            ? portQueue.getParent().getSlug() : NULL_PORT;
        return parentSlug + DELIM + "Q." + portQueue.getId();
    }

    /**
     * Returns a string representation of the link endpoint.
     *
     * @param ep the link endpoint.
     * @return string representation.
     */
    public static final String toString(LinkEndpoint ep) {
        // At present, the Endpoint slug is the same as the Queue slug
        return (ep == null) ? NULL_ENDPOINT : ep.getPortQueue().getSlug();
    }

    /**
     * Returns a string representation of the link.
     *
     * @param link the link.
     * @param abbreviate the abbreviate flag.
     * @return string representation.
     */
    public static final String toString(Link link, boolean abbreviate) {
        if (link == null) {
            return NULL_LINK;
        } else if (!abbreviate) {
            return "L" + DELIM + toString(link.getSrc()) + EP_DELIM + toString(link.getDst());
        } else {
            // abbreviated form doesn't include "L::", and only includes valid objects;
            StringBuilder sb = new StringBuilder(64);
            toAbbrString(sb, link.getSrc());
            sb.append(EP_DELIM);
            toAbbrString(sb, link.getDst());
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        return "TopoSlug{"
                + "slug='" + slug + '\''
                + ", type=" + type
                + '}';
    }

    /* Create an abbreviated endpoint - minimize based on real values, vs NULL values */
    private static final void toAbbrString(StringBuilder sb, LinkEndpoint ep) {
        if (ep != null) {
            sb.append(ep.getTopoSwitch().getId());
            if (ep.getSwitchPort() != null && !ep.getSwitchPort().getSlug().equals(NULL_PORT)) {
                sb.append(DELIM).append(ep.getSwitchPort().getId());
            }
            if (ep.getPortQueue() != null && !ep.getPortQueue().getSlug().equals(NULL_ENDPOINT)) {
                sb.append(DELIM).append(ep.getPortQueue().getId());
            }
        } else {
            sb.append(NULL_SWITCH);
        }
    }

    // TODO: We are not using TopoSlug anywhere .. probably should .. if not, kill some of the below
    private final String slug;
    private final Type type;

    // TODO: Consider migrating the static toString methods to the Type enum
    // TODO: Is the enum needed? It'll help identify the slug type .. tighter integration between types and code.
    public enum Type {
        SWITCH('S'), PORT('P'), QUEUE('Q'), LINK('L');

        // Enable a "coded" version of the TopoSlug.Type
        private final char code;

        Type(char code) {
            this.code = code;
        }

        public char getCode() {
            return code;
        }

        /**
         * Gets {@code}Type{@code} from the char.
         *
         * @param code the code
         * @return the {@code}Type{@code}.
         */
        public static Type fromCode(char code) {
            switch (code) {
                case 'S' : return SWITCH;
                case 'P' : return PORT;
                case 'Q' : return QUEUE;
                case 'L' : return LINK;
                default: throw new IllegalArgumentException("Uknown TopoSlug.Type: " + code);
            }
        }
    }

    public TopoSlug(String slug) {
        this.slug = slug;
        this.type = Type.fromCode(slug.charAt(0));
    }

    /**
     *  toLink will convert any slug into a link, possibly with a bunch of null values.
     *  It depends on the type of the slug:
     *      - LINK:     This should be fully formed, no nulls.
     *      - QUEUE:    Only the source endpoint is fully formed (endpoint is equivalent)
     *      - PORT:     The source endpoint will be partially formed.
     *      - SWITCH:   The source endpoint will be partially formed.
     *  This method can be used as a shortcut to parse the slug.
     */
    public static final Link toLink(TopoSlug slug) {
        // TODO: will we create objects from slugs? if so, we'll need to ensure some of the other datamembers
        // are not 'final'
        return null;
    }

}
