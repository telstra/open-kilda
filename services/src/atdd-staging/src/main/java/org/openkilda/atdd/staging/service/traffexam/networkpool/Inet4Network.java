package org.openkilda.atdd.staging.service.traffexam.networkpool;

import java.net.Inet4Address;
import java.net.UnknownHostException;

public class Inet4Network {
    private Inet4Address network;
    private byte prefix;
    private byte mask[];

    public Inet4Network(Inet4Address network, int prefix)
            throws Inet4ValueException {
        this.checkPrefix(prefix, (byte) 0);
        this.prefix = (byte) prefix;

        this.mask = this.makeMask(this.prefix);
        this.network = this.applyMask(network, this.mask);
    }

    public Inet4Network subnet(long index, int prefix)
            throws Inet4ValueException {
        this.checkPrefix(prefix, prefix);

        index <<= 32 - prefix;
        Inet4Address target = this.merge(index);
        this.checkOverflow(target);

        return new Inet4Network(target, prefix);
    }

    public Inet4Address address(long index) throws Inet4ValueException {
        Inet4Address target = this.merge(index);
        this.checkOverflow(target);
        return target;
    }

    public Inet4Address applyMask(Inet4Address address)
            throws Inet4ValueException {
        return this.applyMask(address, this.mask);
    }

    private Inet4Address applyMask(Inet4Address address, byte[] mask)
            throws Inet4ValueException {
        byte unpacked[] = address.getAddress();

        for (int idx = 0; idx < unpacked.length; idx++) {
            unpacked[idx] &= mask[idx];
        }

        try {
            return (Inet4Address) Inet4Address.getByAddress(unpacked);
        } catch (UnknownHostException e) {
            throw new Inet4ValueException("Unable to \"pack\" raw address.", e);
        }
    }

    private byte[] makeMask(int prefix) {
        byte[] mask = new byte[4];

        int shift = 32 - prefix;
        for (int idx = 0; idx < mask.length; idx++, shift -= 8) {
            int blank = 0xff;
            if (0 < shift)
                blank <<= shift;
            mask[mask.length - 1 - idx] = (byte) (blank & 0xff);
        }

        return mask;
    }

    private Inet4Address merge(long add) throws Inet4ValueException {
        byte unpacked[] = new byte[4];

        for (int idx = 0; idx < unpacked.length; idx++) {
            unpacked[unpacked.length - 1 - idx] = (byte)(add & 0xff);
            add >>= 8;
        }

        return this.merge(unpacked);
    }

    private Inet4Address merge(byte add[]) throws Inet4ValueException {
        byte unpacked[] = network.getAddress();

        for (int idx = 0; idx < unpacked.length; idx++) {
            unpacked[idx] |= add[idx];
        }

        try {
            return (Inet4Address) Inet4Address.getByAddress(unpacked);
        } catch (UnknownHostException e) {
            throw new Inet4ValueException("Unable to \"pack\" raw address.", e);
        }
    }

    private void checkPrefix(int prefix, int lowest) throws Inet4ValueException {
        if (prefix < lowest || 32 < prefix) {
            throw new Inet4ValueException(String.format(
                    "Invalid network prefix. Must be in range from %d to 32",
                    lowest));
        }
    }

    private void checkOverflow(Inet4Address subject) throws Inet4ValueException {
        if (! network.equals(this.applyMask(subject))) {
            throw new Inet4ValueException("Request address beyond network");
        }
    }

    public Inet4Address getNetwork() {
        return network;
    }

    public byte getPrefix() {
        return prefix;
    }
}
