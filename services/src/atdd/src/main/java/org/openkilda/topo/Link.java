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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Link)) return false;

		Link link = (Link) o;

		if (src != null ? !src.equals(link.src) : link.src != null) return false;
		return dst != null ? dst.equals(link.dst) : link.dst == null;
	}

	@Override
	public int hashCode() {
		int result = src != null ? src.hashCode() : 0;
		result = 31 * result + (dst != null ? dst.hashCode() : 0);
		return result;
	}

	public static void main(String[] args) {
        LinkEndpoint ep1 = new LinkEndpoint(null,null, null);
        LinkEndpoint ep2 = new LinkEndpoint(null,null, null);
        Link link1 = new Link(ep1,ep2);
        System.out.println("link1.getSlug() = " + link1.getSlug());
    }
}
