package org.bitbucket.kilda.storm.topology.switchevent.activated;

import java.util.Map;

import javax.inject.Inject;

import org.apache.storm.Config;
import org.apache.storm.ILocalCluster;
import static org.apache.storm.Testing.*;
import org.apache.storm.testing.CompleteTopologyParam;
import org.apache.storm.testing.MkClusterParam;
import org.apache.storm.testing.MockedSources;
import org.apache.storm.testing.TestJob;
import org.apache.storm.tuple.Values;
import org.bitbucket.kilda.storm.topology.switchevent.activated.guice.module.TestModule;
import org.bitbucket.kilda.storm.topology.switchevent.activated.guice.module.TestYamlConfigModule;
import org.bitbucket.kilda.storm.topology.switchevent.activated.runner.StormTopologyBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.pvanassen.guicejunitrunner.GuiceJUnitRunner;
import nl.pvanassen.guicejunitrunner.GuiceModules;

@RunWith(GuiceJUnitRunner.class)
@GuiceModules({ TestYamlConfigModule.class, TestModule.class })
public class TopologyTest {

	private static final String SWITCH_ID = "foo";
	
	@Inject
	private StormTopologyBuilder builder;
	
	@Test
	public void verifyEmittedValues() {
		MkClusterParam clusterParam = new MkClusterParam();
		clusterParam.setSupervisors(1);

		withSimulatedTimeLocalCluster(clusterParam, new TestJob() {
			
			@Override
			public void run(ILocalCluster cluster) throws JsonProcessingException {
				
				MockedSources mockedSources = new MockedSources();
				mockedSources.addMockData(builder.getSpoutId(), new Values(SWITCH_ID));

				Config config = new Config();
				config.setDebug(true);

				CompleteTopologyParam topologyParam = new CompleteTopologyParam();
				topologyParam.setMockedSources(mockedSources);
				topologyParam.setStormConf(config);

				Map<?, ?> result = completeTopology(cluster, builder.build(), topologyParam);
				assertTrue(multiseteq(new Values(new Values(SWITCH_ID)),
						readTuples(result, builder.getSpoutId())));
				assertTrue(multiseteq(new Values(new Values(SWITCH_ID)),
						readTuples(result, builder.getConfirmationBoltId())));
			}
			
		});
	}

}
