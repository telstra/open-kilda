package org.bitbucket.openkilda.tools.mininet;

import static org.junit.Assert.*;

import java.util.List;

import org.bitbucket.openkilda.tools.mininet.IMininet;
import org.bitbucket.openkilda.tools.mininet.IMininetSwitch;
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

  public static final String MININET_ADDRESS = "192.168.56.10";
  public static final int MININET_PORT = 38080;
  
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
  public void testTDD() {
    IPv4Address controllerAddress = IPv4Address.of("127.0.0.1");
    TransportPort controllerPort = TransportPort.of(6653);
    OFVersion ofVersion = OFVersion.OF_13;
    MininetController controller = new MininetController()
        .setIP(controllerAddress)
        .setPort(controllerPort)
        .setVersion(ofVersion)
        .build();
    
    IMininet mininet = new Mininet()
        .addController(controller)
        .addSwitch("sw1", DatapathId.of(1))
        .addSwitch("sw2", DatapathId.of(2))
        .addLink("sw1", "sw2")
        .addMininetServer(MININET_ADDRESS, MININET_PORT)
        .build();
    
    List<IMininetSwitch> switches = mininet.switches();
    assertEquals("failure - should have exactly 2 switches", 2, switches.size());
  }

}
