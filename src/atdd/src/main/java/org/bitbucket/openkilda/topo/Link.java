package org.bitbucket.openkilda.topo;

/**
 * Links represent physical connections between Switches. They attach to a port
 * on the Switch.
 */
public class Link implements ITopoSlug {

	private final LinkEndpoint src;
	private final LinkEndpoint dst;
	private String slug;
	private String shortSlug;

	public Link(LinkEndpoint src, LinkEndpoint dst) {
		this.src = src;
		this.dst = dst;
	}

	public LinkEndpoint getSrc() {
		return src;
	}

	public LinkEndpoint getDst() {
		return dst;
	}

	@Override
    public String getSlug() {
		if (slug == null)
			slug = TopoSlug.toString(this,false);
		return slug;
	}

	public String getShortSlug() {
		if (shortSlug == null)
			shortSlug = TopoSlug.toString(this,true);
		return shortSlug;
	}

	public static void main(String[] args) {
        LinkEndpoint ep1 = new LinkEndpoint(null,null, null);
        LinkEndpoint ep2 = new LinkEndpoint(null,null, null);
        Link link1 = new Link(ep1,ep2);
        System.out.println("link1.getSlug() = " + link1.getSlug());
    }
}
