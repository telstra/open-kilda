package org.bitbucket.openkilda.messaging.command.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bitbucket.openkilda.messaging.AbstractSerializer;
import org.bitbucket.openkilda.messaging.CommandMessage;
import org.bitbucket.openkilda.messaging.Message;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.UUID;

@Ignore
public abstract class AbstractSerializerTest implements AbstractSerializer {
    private static final String FLOW_NAME = "test_flow";
    private static final String SWITCH_ID = "00:00:00:00:00:00:00:00";
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final long TIMESTAMP = System.currentTimeMillis();
    private static final int INPUT_PORT = 1;
    private static final int OUTPUT_PORT = 2;
    private static final int INPUT_VLAN_ID = 101;
    private static final int OUTPUT_VLAN_ID = 102;
    private static final int TRANSIT_VLAN_ID = 103;
    private static final int BANDWIDTH = 10000;
    private static final int METER_ID = 1;
    private static final int SOURCE_METER_ID = 2;
    private static final int DESTINATION_METER_ID = 3;
    private static final OutputVlanType OUTPUT_VLAN_TYPE = OutputVlanType.REPLACE;

    @Test
    public void serializeInstallEgressFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallEgressFlow data = new InstallEgressFlow(FLOW_NAME, SWITCH_ID, INPUT_PORT, OUTPUT_PORT,
                TRANSIT_VLAN_ID, OUTPUT_VLAN_ID, OUTPUT_VLAN_TYPE);
        System.out.println(data);

        CommandMessage command = new CommandMessage();
        command.setData(data);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);
        System.out.println(String.format("message type: %s", message.getType()));

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallEgressFlow);

        InstallEgressFlow resultData = (InstallEgressFlow) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
    }

    @Test
    public void serializeInstallIngressFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallIngressFlow data = new InstallIngressFlow(FLOW_NAME, SWITCH_ID, INPUT_PORT, OUTPUT_PORT,
                INPUT_VLAN_ID, TRANSIT_VLAN_ID, OUTPUT_VLAN_TYPE, BANDWIDTH, METER_ID);
        System.out.println(data);

        CommandMessage command = new CommandMessage();
        command.setData(data);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);
        System.out.println(String.format("message type: %s", message.getType()));

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallIngressFlow);

        InstallIngressFlow resultData = (InstallIngressFlow) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
    }

    @Test
    public void serializeInstallTransitFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallTransitFlow data = new InstallTransitFlow(FLOW_NAME, SWITCH_ID, INPUT_PORT, OUTPUT_PORT,
                TRANSIT_VLAN_ID);
        System.out.println(data);

        CommandMessage command = new CommandMessage();
        command.setData(data);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);
        System.out.println(String.format("message type: %s", message.getType()));

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallTransitFlow);

        InstallTransitFlow resultData = (InstallTransitFlow) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
    }

    @Test
    public void serializeInstallOneSwitchFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallOneSwitchFlow data = new InstallOneSwitchFlow(FLOW_NAME, SWITCH_ID, INPUT_PORT, OUTPUT_PORT,
                INPUT_VLAN_ID, OUTPUT_VLAN_ID, OUTPUT_VLAN_TYPE, BANDWIDTH, SOURCE_METER_ID, DESTINATION_METER_ID);
        System.out.println(data);

        CommandMessage command = new CommandMessage();
        command.setData(data);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);
        System.out.println(String.format("message type: %s", message.getType()));

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallOneSwitchFlow);

        InstallOneSwitchFlow resultData = (InstallOneSwitchFlow) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
    }
}
