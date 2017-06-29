package org.bitbucket.openkilda.tools.maxinet;

import static org.junit.Assert.*;

import javax.inject.Inject;

import org.bitbucket.openkilda.tools.maxinet.exception.MaxinetClientException;
import org.bitbucket.openkilda.tools.maxinet.exception.MaxinetInternalException;
import org.bitbucket.openkilda.tools.maxinet.guice.Module;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import nl.pvanassen.guicejunitrunner.GuiceJUnitRunner;
import nl.pvanassen.guicejunitrunner.GuiceModules;

@RunWith(GuiceJUnitRunner.class)
@GuiceModules({ Module.class })
public class MaxinetTest {

	private static Integer TOO_MANY_WORKERS = 100;
	
	@Rule
	public TestName name = new TestName();

	@Inject
	private IMaxinet maxinet;

	@Test
	public void dynamicTopo() {
		Topo topo = new Topo();
		Host h1 = new Host("h1");
		Host h2 = new Host("h2");
		Switch s1 = new Switch("s1");

		topo.host(h1)
		    .host(h2)
		    ._switch(s1)
		    .link(new Link(h1, s1))
		    .link(new Link(h2, s1));

		try {
			maxinet = maxinet.cluster("setupExperimentWithBasicTopo", 2, 2)
			                 .experiment("myexperiment", topo)
			                 .setup()
			                 .sleep(5000L)
			                 .run(h1.getName(), "ping -c 5 10.0.0.2")
			                 ._switch("s2", 1)
			                 .host("h3", "s2")
			                 .host("h4", "s2")
			                 .link(topo.getSwitch("s1"), topo.getSwitch("s2"))
			                 .link(topo.getHost("h3"), topo.getSwitch("s2"))
			                 .link(topo.getHost("h4"), topo.getSwitch("s2"))
			                 .sleep(2000L)
			                 .run(topo.getHost("h3").getName(), "ping -c 5 10.0.0.4")
			                 .run(topo.getHost("h3").getName(), "ping -c 5 10.0.0.1");
			
		} catch (Exception e) {
			// Annoyingly, ProcessingException is caught here with my exception as the cause. I just want my exception!
			e.printStackTrace();
			fail(e.getCause().getMessage());
		} finally {
			maxinet.stop();
		}
	}
	
	//@Test
	public void tooManyWorkers() {
		boolean gotException = false;
		try {
			maxinet = maxinet.cluster("tooManyWorkers", TOO_MANY_WORKERS, TOO_MANY_WORKERS);
		} catch (Exception e) {
			gotException = true;
			assertTrue("expected internal exception when too many workers requested, got " + e.getCause().getClass(), e.getCause() instanceof MaxinetInternalException);
		}
		assertTrue("expected exception when too many workers requested", gotException);
	}

	//@Test
	public void unnamedCluster() {
		boolean gotException = false;
		try {
			maxinet = maxinet.cluster(null, 1, 1);
		} catch (Exception e) {
			gotException = true;
			assertTrue("expected client exception when cluster unnamed, got " + e.getCause().getClass(), e.getCause() instanceof MaxinetClientException);
		}
		assertTrue("expected exception when when cluster unnamed", gotException);
	}
	
}
