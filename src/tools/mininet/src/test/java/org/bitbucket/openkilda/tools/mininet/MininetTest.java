package org.bitbucket.openkilda.tools.mininet;

import static org.junit.Assert.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

import org.bitbucket.openkilda.tools.mininet.Mininet;
import org.bitbucket.openkilda.tools.mininet.MininetController;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;

public class MininetTest {

  public static final String MININET_ADDRESS = "127.0.0.1";
  public static final int MININET_PORT = 38080;
  public static final int MAX_CONNECT_TIME = 5000;
  public static final int SLEEP_INTERVAL = 1000;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testTDD() throws Exception {
    String controllerAddress = "kilda";
    TransportPort controllerPort = TransportPort.of(6653);
    OFVersion ofVersion = OFVersion.OF_13;
    IMininetController controller = new MininetController()
        .setIP(controllerAddress)
        .setPort(controllerPort)
        .setVersion(ofVersion)
        .setName("floodlight")
        .build();

    IMininet mininet = new Mininet()
        .addMininetServer(MININET_ADDRESS, MININET_PORT)
        .clear()
        .addController(controller)
        .addSwitch("sw1", DatapathId.of(1))
        .addSwitch("sw2", DatapathId.of(2))
        .addLink("sw1", "sw2")
        .build();

    List<MininetSwitch> switches = mininet.switches().getSwitches();
    assertEquals("failure - should have exactly 2 switches", 2, switches.size());

    Thread.sleep(MAX_CONNECT_TIME);
    switches = mininet.switches().getSwitches();
    for(MininetSwitch sw: switches) {
      assertTrue(String.format("failure - %s should be connected", sw.getName()), sw.getConnected());
    }

    List<MininetLink> links = mininet.links().getLinks();
    for(MininetLink link: links) {
      assertTrue(String.format("failure - %s should be up", link.getName()), link.isUp());
    }
  }
}
