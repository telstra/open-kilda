package org.bitbucket.kilda.storm.topology.switchevent.activated.model;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.storm.tuple.Values;
import org.bitbucket.kilda.storm.topology.kafka.OutputFields;
import org.bitbucket.kilda.storm.topology.kafka.TupleProducer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "switch_id"
})
@OutputFields(ActivatedSwitchEvent.OUTPUT_FIELD_SWITCH_ID)
public class ActivatedSwitchEvent implements TupleProducer {
	
	public static final String OUTPUT_FIELD_SWITCH_ID = "switchId";
	
	public ActivatedSwitchEvent() {
	}
	
	public ActivatedSwitchEvent(String switchId) {
		super();
		this.switchId = switchId;
	}

	@JsonProperty("switch_id")
	private String switchId;

	public String getSwitchId() {
		return switchId;
	}

	public void setSwitchId(String switchId) {
		this.switchId = switchId;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, 
				ToStringStyle.SIMPLE_STYLE, true, true);
	}
	
	public Values toTuple() {
		// Order MUST match @OutputField value order!
	    return new Values(switchId);	
	}

}
