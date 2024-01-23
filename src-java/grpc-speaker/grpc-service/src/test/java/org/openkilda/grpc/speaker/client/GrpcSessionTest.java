package org.openkilda.grpc.speaker.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openkilda.grpc.speaker.exception.GrpcRequestFailureException;

class GrpcSessionTest {

    @Test
    void makeChannelWithValidAddressShouldCreateChannel() {
        String address = "192.168.1.1";
        assertNotNull(GrpcSession.makeChannel(address));
    }

    @Test
    void makeChannelWithInvalidAddressShouldProduceException() {
        GrpcRequestFailureException exception = Assertions.assertThrows(
                GrpcRequestFailureException.class, () -> GrpcSession.makeChannel("a.b.c.d"));

        Assertions.assertEquals("IP address is not valid.", exception.getMessage());
    }
}