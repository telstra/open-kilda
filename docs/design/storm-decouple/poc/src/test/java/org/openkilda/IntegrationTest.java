package org.openkilda;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

@RunWith(MockitoJUnitRunner.class)
public class IntegrationTest {

    @Mock
    IDecisionMakerCarrier carrier;

    @Before
    public void setup() {
        reset(carrier);
    }

    @Test
    public void happyPath() {
        IntegrationCarrier integrationCarrier = new IntegrationCarrier() {
            @Override
            public void sendDiscovery(SwitchId switchId, int portNo, long packetNo, long currentTime) {
                watcherService.confirmation(switchId, portNo, packetNo);
                watcherService.discovery(switchId, portNo, new SwitchId(10), 10, packetNo, currentTime+1);
            }
        };
        WatchListService watchListService = new WatchListService(integrationCarrier, 10);
        WatcherService watcherService = new WatcherService(integrationCarrier, 100);
        DecisionMakerService decisionMakerService = new DecisionMakerService(carrier, 200, 100);
        integrationCarrier.setWatchListService(watchListService);
        integrationCarrier.setWatcherService(watcherService);
        integrationCarrier.setDecisionMakerService(decisionMakerService);

        watchListService.addWatch(new SwitchId(1), 1, 1);
        verify(carrier).discovered(eq(new SwitchId(1)), eq(1), eq(new SwitchId(10)), eq(10), anyLong());
    }

    @Test
    public void failed() {
        IntegrationCarrier integrationCarrier = new IntegrationCarrier() {
            @Override
            public void sendDiscovery(SwitchId switchId, int portNo, long packetNo, long currentTime) {
                watcherService.confirmation(switchId, portNo, packetNo);
            }
        };
        WatchListService watchListService = new WatchListService(integrationCarrier, 10);
        WatcherService watcherService = new WatcherService(integrationCarrier, 100);
        DecisionMakerService decisionMakerService = new DecisionMakerService(carrier, 200, 100);
        integrationCarrier.setWatchListService(watchListService);
        integrationCarrier.setWatcherService(watcherService);
        integrationCarrier.setDecisionMakerService(decisionMakerService);

        watchListService.addWatch(new SwitchId(1), 1, 0);

        for (int i = 1; i <= 200; ++i) {
            watchListService.tick(i);
            watcherService.tick(i);
        }

        verify(carrier).failed(eq(new SwitchId(1)), eq(1), anyLong());
    }

    static class IntegrationCarrier implements IWatchListServiceCarrier, IWatcherServiceCarrier {

        WatcherService watcherService;
        WatchListService watchListService;
        DecisionMakerService decisionMakerService;

        public void setDecisionMakerService(DecisionMakerService decisionMakerService) {
            this.decisionMakerService = decisionMakerService;
        }

        public void setWatcherService(WatcherService watcherService) {
            this.watcherService = watcherService;
        }

        public void setWatchListService(WatchListService watchListService) {
            this.watchListService = watchListService;
        }

        @Override
        public void watchRemoved(SwitchId switchId, int portNo, long currentTime) {

        }

        @Override
        public void discoveryRequest(SwitchId switchId, int portNo, long currentTime) {
            watcherService.addWatch(switchId, portNo, currentTime);
        }

        @Override
        public void discovered(SwitchId switchId, int portNo, SwitchId endSwitchId, int endPortNo, long currentTime) {
            decisionMakerService.discovered(switchId, portNo, endSwitchId, endPortNo, currentTime);
        }

        @Override
        public void failed(SwitchId switchId, int portNo, long currentTime) {
            decisionMakerService.failed(switchId, portNo, currentTime);
        }

        @Override
        public void sendDiscovery(SwitchId switchId, int portNo, long packetNo, long currentTime) {
           throw new NotImplementedException();
        }
    }
}
