package org.bitbucket.openkilda.tools.maxinet;

import javax.inject.Inject;

import org.bitbucket.openkilda.tools.maxinet.guice.Module;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import nl.pvanassen.guicejunitrunner.GuiceJUnitRunner;
import nl.pvanassen.guicejunitrunner.GuiceModules;

@RunWith(GuiceJUnitRunner.class)
// TODO - modify GuiceModules so we can use Module constructors other than the default?
@GuiceModules({ Module.class })
public class AppTest {

	@Rule
	public TestName name = new TestName();
	
	@Inject
	private IFrontend frontend;
	
	@Test
	public void createTopo() {
		Topo topo = new Topo();
		Host h1 = new Host("h1");
		Host h2 = new Host("h2");
		Switch s1 = new Switch("s1");
		
		topo.addHost(h1);
		topo.addHost(h2);
		topo.addSwitch(s1);
		
		topo.addLink(new Link(h1, s1));
		topo.addLink(new Link(h2, s1));
		
		try {
			ICluster cluster = frontend.cluster("mycluster", 1, 1).start();
			System.out.println("cluster name is " + cluster.getName());
			IExperiment experiment = frontend.experiment("myexperiment", cluster, topo);
			experiment.setup();
			System.out.println("experiment name is " + experiment.getName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
