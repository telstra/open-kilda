package org.bitbucket.kilda.storm.topology.switchevent.activated.bolt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "correlated"
})
public class Correlation {
	
	private boolean correlated;

	public Correlation() {		
	}
	
	public Correlation(boolean correlated) {
		this.correlated = correlated;
	}

	public void setCorrelated(boolean correlated) {
		this.correlated = correlated;
	}

	public boolean isCorrelated() {
		return correlated;
	}

}
