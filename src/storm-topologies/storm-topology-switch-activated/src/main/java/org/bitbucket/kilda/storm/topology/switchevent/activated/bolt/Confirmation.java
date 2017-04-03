package org.bitbucket.kilda.storm.topology.switchevent.activated.bolt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "confirmed"
})
public class Confirmation {
	
	private boolean confirmed;

	public Confirmation() {		
	}
	
	public Confirmation(boolean confirmed) {
		this.confirmed = confirmed;
	}

	public void setConfirmed(boolean confirmed) {
		this.confirmed = confirmed;
	}

	public boolean isConfirmed() {
		return confirmed;
	}

}
