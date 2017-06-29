package org.bitbucket.openkilda.topo;

import java.net.URISyntaxException;

import static org.bitbucket.openkilda.topo.TopoSlug.Type.LINK;

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

    public static final String toString(Switch aSwitch) {
        return "S." + aSwitch.getId();
    }

    public static final String toString(Port port) {
        // NB: mostly a bug to have a null parent .. but allow it for now
        String parentSlug = (port.getParent() != null) ? port.getParent().getSlug() : NULL_SWITCH;
        return parentSlug + DELIM + "P." + port.getId();
    }

    public static final String toString(PortQueue portQueue) {
        String parentSlug = (portQueue.getParent() != null)
                            ? portQueue.getParent().getSlug() : NULL_PORT;
        return parentSlug + DELIM + "Q." + portQueue.getId();
    }

    public static final String toString(LinkEndpoint ep){
        // At present, the Endpoint slug is the same as the Queue slug
        return (ep == null) ? NULL_ENDPOINT : ep.getPortQueue().getSlug();
    }

    public static final String toString (Link link, boolean abbreviate){
        if (link == null) {
            return NULL_LINK;
        } else if (!abbreviate) {
            return "L" + DELIM + toString(link.getSrc()) + EP_DELIM + toString(link.getDst());
        } else {
            // abbreviated form doesn't include "L::", and only includes valid objects;
            StringBuilder sb = new StringBuilder(64);
            toAbbrString(sb,link.getSrc());
            sb.append(EP_DELIM);
            toAbbrString(sb,link.getDst());
            return sb.toString();
        }
    }


    /* Create an abbreviated endpoint - minimize based on real values, vs NULL values */
    private static final void toAbbrString(StringBuilder sb, LinkEndpoint ep){
        if (ep != null) {
            sb.append(ep.getTopoSwitch().getId());
            if (ep.getSwitchPort() != null && ep.getSwitchPort().getSlug() != NULL_PORT)
                sb.append(DELIM).append(ep.getSwitchPort().getId());
            if (ep.getPortQueue() != null && ep.getPortQueue().getSlug() != NULL_ENDPOINT)
                sb.append(DELIM).append(ep.getPortQueue().getId());
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
        SWITCH('S'), PORT('P'), QUEUE('Q'), LINK('L') ;

        // Enable a "coded" version of the TopoSlug.Type
        private final char code;
        Type(char code){
            this.code = code;
        }
        public char getCode() {
            return code;
        }
        public static Type fromCode(char code){
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
        switch(this.type) {
            case SWITCH: break;
            case PORT: break;
            case QUEUE: break;
            case LINK: break;
        }
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
    public static final Link toLink (TopoSlug slug){
        // TODO: will we create objects from slugs? if so, we'll need to ensure some of the other datamembers are not 'final'
        return null;
    }


    @Override
    public String toString() {
        return "TopoSlug{" +
                "slug='" + slug + '\'' +
                ", type=" + type +
                '}';
    }

    public static void main(String[] args) {
        TopoSlug ts = new TopoSlug("Q:123:145:367");

        System.out.println("WTF: " + ts);
    }


}
