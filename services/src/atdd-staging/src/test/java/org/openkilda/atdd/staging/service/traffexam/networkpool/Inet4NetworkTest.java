package org.openkilda.atdd.staging.service.traffexam.networkpool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Inet4NetworkTest {

    @Test
    public void subnet() throws UnknownHostException, Inet4ValueException {
        Inet4Address address = (Inet4Address) Inet4Address.getByName(
                "172.16.255.0");
        Inet4Network subject = new Inet4Network(address, 29);

        assertEquals(InetAddress.getByName("172.16.255.0"), subject.subnet(0, 30).getNetwork());
        assertEquals(InetAddress.getByName("172.16.255.4"), subject.subnet(1, 30).getNetwork());

        boolean raises = false;
        try {
            subject.subnet(2, 30);
        } catch (Inet4ValueException e) {
            raises = true;
        }

        assertTrue(
                String.format(
                        "Here must be %s exception", Inet4ValueException.class),
                raises);
    }

    @Test
    public void address() throws UnknownHostException, Inet4ValueException {
        Inet4Address address = (Inet4Address) Inet4Address.getByName(
                "172.16.255.0");
        Inet4Network subject = new Inet4Network(address, 30);

        assertEquals(InetAddress.getByName("172.16.255.0"), subject.address(0));
        assertEquals(InetAddress.getByName("172.16.255.1"), subject.address(1));
        assertEquals(InetAddress.getByName("172.16.255.2"), subject.address(2));
        assertEquals(InetAddress.getByName("172.16.255.3"), subject.address(3));

        boolean raises = false;
        try {
            subject.address(4);
        } catch (Inet4ValueException e) {
            raises = true;
        }
        assertTrue(
                String.format(
                        "Here must be %s exception", Inet4ValueException.class),
                raises);
    }
}
