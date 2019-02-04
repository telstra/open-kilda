package org.openkilda;


import lombok.Value;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class WatcherService {
    private final IWatcherServiceCarrier carrier;
    private final long awaitTime;
    private long packetNo = 0;
    private Set<Packet> confirmations = new HashSet<>();
    private SortedMap<Long, Set<Packet>> timeouts = new TreeMap<>();

    public WatcherService(IWatcherServiceCarrier carrier, long awaitTime) {
        this.carrier = carrier;
        this.awaitTime = awaitTime;
    }

    public void addWatch(SwitchId switchId, int portNo, long currentTime) {
        Packet packet = Packet.of(switchId, portNo, packetNo);
        timeouts.computeIfAbsent(currentTime + awaitTime, mappingFunction -> new HashSet<>())
                .add(packet);
        carrier.sendDiscovery(switchId, portNo, packetNo, currentTime);
        packetNo += 1;
    }


    public void removeWatch(SwitchId switchId, int portNo) {

    }

    public void tick(long tickTime) {
        // TODO: move to stream processing
        SortedMap<Long, Set<Packet>> range = timeouts.subMap(0L, tickTime + 1);
        if (!range.isEmpty()) {
            for (Set<Packet> e : range.values()) {
                for (Packet ee : e) {
                    if (confirmations.remove(ee)) {
                        carrier.failed(ee.switchId, ee.port, tickTime);
                    }
                }
            }
            range.clear();
        }
    }

    public void confirmation(SwitchId switchId, int portNo, long packetNo) {
        Packet packet = Packet.of(switchId, portNo, packetNo);
        confirmations.add(packet);
    }

    public void discovery(SwitchId switchId, int portNo, SwitchId endSwitchId, int endPortNo, long packetNo, long currentTime) {
        Packet packet = Packet.of(switchId, portNo, packetNo);
        confirmations.remove(packet);
        carrier.discovered(switchId, portNo, endSwitchId, endPortNo, currentTime);
    }

    public Set<Packet> getConfirmations() {
        return confirmations;
    }

    public SortedMap<Long, Set<Packet>> getTimeouts() {
        return timeouts;
    }

    public static @Value(staticConstructor = "of")
    class Packet {
        private final SwitchId switchId;
        private final int port;
        private final long packetNo;
    }
}
