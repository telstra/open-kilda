package org.bitbucket.kilda.storm.topology.switchevent.activated.runner;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.bitbucket.kilda.storm.topology.kafka.KafkaSpoutFactory;
import org.bitbucket.kilda.storm.topology.runner.IStormTopologyBuilder;
import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.ConfirmationBolt;
import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.CorrelationBolt;
import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.IConfirmer;
import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.ICorrelator;
import org.bitbucket.kilda.storm.topology.switchevent.activated.model.ActivatedSwitchEvent;

@Named
public class StormTopologyBuilder implements IStormTopologyBuilder {

	@Inject
	@Named("storm.topology.name")	
	private String topologyName;
	
	@Inject
	@Named("kafka.topic.name")	
	private String topicName;
		
	@Inject
	@Named("kafka.spout.id")	
	private String spoutId;

	@Inject
	@Named("kafka.spout.tasks")
	private Integer spoutTasks;
	
	@Inject
	@Named("kafka.bolt.confirmation.id")
	private String confirmationBoltId;
	
	@Inject
	@Named("kafka.bolt.confirmation.parallelismHint")
	private Integer confirmationBoltParallelismHint;

	@Inject
	@Named("kafka.bolt.correlation.id")
	private String correlationBoltId;
	
	@Inject
	@Named("kafka.bolt.correlation.parallelismHint")
	private Integer correlationBoltParallelismHint;
	
	@Inject
	private KafkaSpoutFactory spoutFactory;
	
	@Inject
	// TODO possibly move injection to bolt itself (not sure how to get storm to place nice with Guice yet)
	private IConfirmer confirmer;

	@Inject
	// TODO possibly move injection to bolt itself (not sure how to get storm to place nice with Guice yet)
	private ICorrelator correlator;
	
	public StormTopology build() {
		return buildWithSpout(null);
	}

	public StormTopology buildWithSpout(BaseRichSpout spout) {
		TopologyBuilder builder = new TopologyBuilder();
		builder.setSpout(spoutId, spoutFactory.create(topicName, ActivatedSwitchEvent.class), spoutTasks);
		builder.setBolt(confirmationBoltId, new ConfirmationBolt(confirmer), confirmationBoltParallelismHint).fieldsGrouping(spoutId, new Fields("switchId"));
		builder.setBolt(correlationBoltId, new CorrelationBolt(correlator), correlationBoltParallelismHint).fieldsGrouping(confirmationBoltId, new Fields("switchId"));
			
		// TODO - send a message about the confirmed and correlated switch!
		
		return builder.createTopology();
	}

	public String getTopologyName() {
		return topologyName;
	}

	public String getSpoutId() {
		return spoutId;
	}

	public String getConfirmationBoltId() {
		return confirmationBoltId;
	}
	
}