package org.bitbucket.openkilda.pathvalidation;

import static org.junit.Assert.*;

import org.bitbucket.openkilda.tools.mininet.IMininet;
import org.bitbucket.openkilda.tools.mininet.IMininetController;
import org.bitbucket.openkilda.tools.mininet.Mininet;
import org.bitbucket.openkilda.tools.mininet.MininetController;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.TransportPort;

public class PathValidationTest {
  
  public static final String MININET_ADDRESS = "127.0.0.1";
  public static final int MININET_PORT = 38080;
  public static final String CONTROLLER_ADDRESS = "kilda";
  public static final TransportPort CONTROLLER_PORT = TransportPort.of(6653);
  protected IMininet mininet;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  @Before
  public void setUp() throws Exception {
    // Create the mininet topology
    String controllerAddress = "kilda";
    TransportPort controllerPort = CONTROLLER_PORT;
    OFVersion ofVersion = OFVersion.OF_13;
    IMininetController controller = new MininetController()
        .setIP(controllerAddress)
        .setPort(controllerPort)
        .setVersion(ofVersion)
        .setName("floodlight")
        .build();

    mininet = new Mininet()
        .addMininetServer(MININET_ADDRESS, MININET_PORT)
        .clear()
        .addController(controller)
        .addSwitch("sw1", DatapathId.of(1))
        .addSwitch("sw2", DatapathId.of(2))
        .addLink("sw1", "sw2")
        .build();
  }

  @After
  public void tearDown() throws Exception {
    mininet.clear();
  }

  @Test
  public void test() {
    assertTrue(String.format("failue - not connected to mininet at %s", MININET_ADDRESS), mininet.isConnect());
  }

}
