/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.network.controller;

import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.messaging.model.SwitchReference;
import org.openkilda.model.BfdSession;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ConstraintViolationException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.BfdSessionRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.controller.BfdPortFsm.BfdPortFsmContext;
import org.openkilda.wfm.topology.network.controller.BfdPortFsm.BfdPortFsmEvent;
import org.openkilda.wfm.topology.network.controller.BfdPortFsm.BfdPortFsmState;
import org.openkilda.wfm.topology.network.error.SwitchReferenceLookupException;
import org.openkilda.wfm.topology.network.model.BfdDescriptor;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.IslReference;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.service.IBfdPortCarrier;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.Condition;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.Random;

@Slf4j
public final class BfdPortFsm extends
        AbstractBaseFsm<BfdPortFsm, BfdPortFsmState, BfdPortFsmEvent,
                        BfdPortFsmContext> {
    private static final int BFD_UDP_PORT = 3784;
    private static int bfdPollInterval = 350;  // TODO: use config option
    private static short bfdFailCycleLimit = 3;  // TODO: use config option

    private final SwitchRepository switchRepository;
    private final BfdSessionRepository bfdSessionRepository;

    private final Random random = new Random();

    @Getter
    private final Endpoint physicalEndpoint;
    @Getter
    private final Endpoint logicalEndpoint;

    private LinkStatus linkStatus = null;

    private String pendingRequestKey = null;
    private BfdDescriptor sessionDescriptor = null;

    private static final StateMachineBuilder<BfdPortFsm, BfdPortFsmState, BfdPortFsmEvent, BfdPortFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                BfdPortFsm.class, BfdPortFsmState.class, BfdPortFsmEvent.class, BfdPortFsmContext.class,
                // extra parameters
                PersistenceManager.class, Endpoint.class, Integer.class);

        // INIT
        builder.transition()
                .from(BfdPortFsmState.INIT).to(BfdPortFsmState.INIT_CHOICE).on(BfdPortFsmEvent.HISTORY)
                .callMethod("consumeHistory");

        // INIT_CHOICE
        builder.transition()
                .from(BfdPortFsmState.INIT_CHOICE).to(BfdPortFsmState.IDLE).on(BfdPortFsmEvent._INIT_CHOICE_CLEAN);
        builder.transition()
                .from(BfdPortFsmState.INIT_CHOICE).to(BfdPortFsmState.CLEANING).on(BfdPortFsmEvent._INIT_CHOICE_DIRTY);
        builder.onEntry(BfdPortFsmState.INIT_CHOICE)
                .callMethod("handleInitChoice");

        // IDLE
        builder.transition()
                .from(BfdPortFsmState.IDLE).to(BfdPortFsmState.INSTALLING).on(BfdPortFsmEvent.ENABLE);
        builder.transition()
                .from(BfdPortFsmState.IDLE).to(BfdPortFsmState.FAIL).on(BfdPortFsmEvent.PORT_UP);
        builder.onEntry(BfdPortFsmState.IDLE)
                .callMethod("idleEnter");

        // INSTALLING
        builder.transition()
                .from(BfdPortFsmState.INSTALLING).to(BfdPortFsmState.ACTIVE).on(BfdPortFsmEvent.PORT_UP)
                .callMethod("reportSetupComplete");
        builder.transition()
                .from(BfdPortFsmState.INSTALLING).to(BfdPortFsmState.CLEANING).on(BfdPortFsmEvent.SPEAKER_FAIL)
                .when(new ResponseKeyValidator())
                .callMethod("reportSpeakerFailure");
        builder.transition()
                .from(BfdPortFsmState.INSTALLING).to(BfdPortFsmState.CLEANING).on(BfdPortFsmEvent.DISABLE);
        builder.transition()
                .from(BfdPortFsmState.INSTALLING).to(BfdPortFsmState.CLEANING).on(BfdPortFsmEvent.BI_ISL_MOVE);
        builder.transition()
                .from(BfdPortFsmState.INSTALLING).to(BfdPortFsmState.FAIL).on(BfdPortFsmEvent.FAIL);
        builder.onEntry(BfdPortFsmState.INSTALLING)
                .callMethod("installingEnter");

        // CLEANING
        builder.transition()
                .from(BfdPortFsmState.CLEANING).to(BfdPortFsmState.CLEANING_CHOICE).on(BfdPortFsmEvent.SPEAKER_SUCCESS)
                .when(new ResponseKeyValidator())
                .callMethod("releaseResources");
        builder.transition()
                .from(BfdPortFsmState.CLEANING).to(BfdPortFsmState.FAIL).on(BfdPortFsmEvent.SPEAKER_FAIL)
                .when(new ResponseKeyValidator())
                .callMethod("reportSpeakerFailure");
        builder.transition()
                .from(BfdPortFsmState.CLEANING).to(BfdPortFsmState.HOUSEKEEPING).on(BfdPortFsmEvent.KILL);
        builder.internalTransition().within(BfdPortFsmState.CLEANING).on(BfdPortFsmEvent.PORT_UP)
                .callMethod("cleaningUpdateLinkStatus");
        builder.internalTransition().within(BfdPortFsmState.CLEANING).on(BfdPortFsmEvent.PORT_DOWN)
                .callMethod("cleaningUpdateLinkStatus");
        builder.onEntry(BfdPortFsmState.CLEANING)
                .callMethod("cleaningEnter");
        builder.onExit(BfdPortFsmState.CLEANING)
                .callMethod("cleaningExit");

        // CLEANING_CHOICE
        builder.transition()
                .from(BfdPortFsmState.CLEANING_CHOICE).to(BfdPortFsmState.IDLE)
                .on(BfdPortFsmEvent._CLEANING_CHOICE_READY);
        builder.transition()
                .from(BfdPortFsmState.CLEANING_CHOICE).to(BfdPortFsmState.WAIT_RELEASE)
                .on(BfdPortFsmEvent._CLEANING_CHOICE_HOLD);
        builder.onEntry(BfdPortFsmState.CLEANING_CHOICE)
                .callMethod("handleCleaningChoice");

        // WAIT_RELEASE
        builder.transition()
                .from(BfdPortFsmState.WAIT_RELEASE).to(BfdPortFsmState.IDLE).on(BfdPortFsmEvent.PORT_DOWN);
        builder.onEntry(BfdPortFsmState.WAIT_RELEASE)
                .callMethod("waitReleaseEnter");
        builder.onExit(BfdPortFsmState.WAIT_RELEASE)
                .callMethod("waitReleaseExit");

        // ACTIVE
        builder.transition()
                .from(BfdPortFsmState.ACTIVE).to(BfdPortFsmState.CLEANING).on(BfdPortFsmEvent.DISABLE);
        builder.transition()
                .from(BfdPortFsmState.ACTIVE).to(BfdPortFsmState.CLEANING).on(BfdPortFsmEvent.BI_ISL_MOVE);
        builder.transition()
                .from(BfdPortFsmState.ACTIVE).to(BfdPortFsmState.HOUSEKEEPING).on(BfdPortFsmEvent.KILL);
        builder.defineSequentialStatesOn(
                BfdPortFsmState.ACTIVE,
                BfdPortFsmState.UP, BfdPortFsmState.DOWN);

        // UP
        builder.transition()
                .from(BfdPortFsmState.UP).to(BfdPortFsmState.DOWN).on(BfdPortFsmEvent.PORT_DOWN);
        builder.onEntry(BfdPortFsmState.UP)
                .callMethod("upEnter");

        // DOWN
        builder.transition()
                .from(BfdPortFsmState.DOWN).to(BfdPortFsmState.UP).on(BfdPortFsmEvent.PORT_UP);
        builder.onEntry(BfdPortFsmState.DOWN)
                .callMethod("downEnter");

        // FAIL
        String reportMalfunctionMethod = "reportMalfunction";
        builder.transition()
                .from(BfdPortFsmState.FAIL).to(BfdPortFsmState.IDLE).on(BfdPortFsmEvent.PORT_DOWN);
        builder.internalTransition().within(BfdPortFsmState.FAIL).on(BfdPortFsmEvent.ENABLE)
                .callMethod(reportMalfunctionMethod);
        builder.internalTransition().within(BfdPortFsmState.FAIL).on(BfdPortFsmEvent.DISABLE)
                .callMethod(reportMalfunctionMethod);
        builder.onEntry(BfdPortFsmState.FAIL)
                .callMethod("failEnter");

        // HOUSEKEEPING
        builder.transition()
                .from(BfdPortFsmState.HOUSEKEEPING).to(BfdPortFsmState.STOP).on(BfdPortFsmEvent.SPEAKER_SUCCESS)
                .callMethod("releaseResources");
        builder.transition()
                .from(BfdPortFsmState.HOUSEKEEPING).to(BfdPortFsmState.STOP).on(BfdPortFsmEvent.SPEAKER_FAIL)
                .callMethod("reportSpeakerFailure");
        builder.onEntry(BfdPortFsmState.HOUSEKEEPING)
                .callMethod("housekeepingEnter");

        builder.defineFinalState(BfdPortFsmState.STOP);
    }

    public static FsmExecutor<BfdPortFsm, BfdPortFsmState, BfdPortFsmEvent, BfdPortFsmContext> makeExecutor() {
        return new FsmExecutor<>(BfdPortFsmEvent.NEXT);
    }

    public static BfdPortFsm create(PersistenceManager persistenceManager, Endpoint endpoint,
                                    Integer physicalPortNumber) {
        return builder.newStateMachine(BfdPortFsmState.INIT, persistenceManager, endpoint, physicalPortNumber);
    }

    public BfdPortFsm(PersistenceManager persistenceManager, Endpoint endpoint, Integer physicalPortNumber) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.bfdSessionRepository = repositoryFactory.createBfdPortRepository();

        this.logicalEndpoint = endpoint;
        this.physicalEndpoint = Endpoint.of(logicalEndpoint.getDatapath(), physicalPortNumber);
    }

    // -- external API --

    public boolean isHousekeeping() {
        return BfdPortFsmState.HOUSEKEEPING == getCurrentState();
    }

    // -- FSM actions --

    public void consumeHistory(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                               BfdPortFsmContext context) {
        Optional<BfdSession> port = loadBfdSession();
        if (port.isPresent()) {
            BfdSession dbView = port.get();
            try {
                sessionDescriptor = BfdDescriptor.builder()
                        .local(makeSwitchReference(dbView.getSwitchId(), dbView.getIpAddress()))
                        .remote(makeSwitchReference(dbView.getRemoteSwitchId(), dbView.getRemoteIpAddress()))
                        .discriminator(dbView.getDiscriminator())
                        .build();
            } catch (SwitchReferenceLookupException e) {
                log.error("{} - unable to use stored BFD session data {} - {}",
                          makeLogPrefix(), dbView, e.getMessage());
            }
        }
    }

    public void handleInitChoice(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                                 BfdPortFsmContext context) {
        if (sessionDescriptor == null) {
            fire(BfdPortFsmEvent._INIT_CHOICE_CLEAN, context);
        } else {
            fire(BfdPortFsmEvent._INIT_CHOICE_DIRTY, context);
        }
    }

    public void idleEnter(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event, BfdPortFsmContext context) {
        logInfo("ready for setup requests");
    }

    public void reportSetupComplete(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                                    BfdPortFsmContext context) {
        logInfo("BFD session setup is successfully completed");
    }

    public void installingEnter(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                                BfdPortFsmContext context) {
        doBfdSetup(context);
    }

    public void releaseResources(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                                 BfdPortFsmContext context) {
        bfdSessionRepository.findBySwitchIdAndPort(logicalEndpoint.getDatapath(), logicalEndpoint.getPortNumber())
                .ifPresent(bfdSessionRepository::delete);
        sessionDescriptor = null;
    }

    public void cleaningEnter(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                              BfdPortFsmContext context) {
        doBfdRemove(context);
    }

    public void cleaningExit(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                             BfdPortFsmContext context) {
        sessionDescriptor = null;
    }

    public void cleaningUpdateLinkStatus(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                                         BfdPortFsmContext context) {
        switch (event) {
            case PORT_UP:
                linkStatus = LinkStatus.UP;
                break;
            case PORT_DOWN:
                linkStatus = LinkStatus.DOWN;
                break;
            default:
                throw new IllegalStateException(String.format("Unable to handle event %s into %s",
                                                              event, getCurrentState()));
        }
    }

    public void handleCleaningChoice(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                                     BfdPortFsmContext context) {
        if (linkStatus != LinkStatus.DOWN) {
            fire(BfdPortFsmEvent._CLEANING_CHOICE_HOLD, context);
        } else {
            fire(BfdPortFsmEvent._CLEANING_CHOICE_READY, context);
        }
    }

    public void waitReleaseEnter(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                                 BfdPortFsmContext context) {
        logInfo("BFD session have been successfully removed, wait for DOWN event for logical port");
    }

    public void waitReleaseExit(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                                BfdPortFsmContext context) {
        linkStatus = LinkStatus.DOWN;
    }

    public void upEnter(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event, BfdPortFsmContext context) {
        logInfo("LINK detected");
        linkStatus = LinkStatus.UP;
        context.getOutput().bfdUpNotification(physicalEndpoint);
    }

    public void downEnter(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event, BfdPortFsmContext context) {
        logInfo("LINK corrupted");
        linkStatus = LinkStatus.DOWN;
        context.getOutput().bfdDownNotification(physicalEndpoint);
    }

    public void failEnter(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event, BfdPortFsmContext context) {
        if (log.isErrorEnabled()) {
            log.error("{} - is marked as FAILED, it can't process any request at this moment",
                      makeLogPrefix());
        }
    }

    public void housekeepingEnter(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                                  BfdPortFsmContext context) {
        logInfo("perform housekeeping - release all resources");
        context.getOutput().bfdKillNotification(physicalEndpoint);

        if (sessionDescriptor != null) {
            doBfdRemove(context);
        }
    }

    public void reportSpeakerFailure(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                                     BfdPortFsmContext context) {

        String prefix;
        if (from == BfdPortFsmState.INSTALLING) {
            prefix = "Got speaker error for BFD-session setup request";
        } else if (from == BfdPortFsmState.CLEANING) {
            prefix = "Got speaker error for BFD-session remove request";
        } else {
            prefix = "Got speaker error";
        }

        BfdSessionResponse response = context.getBfdSessionResponse();
        if (response == null) {
            log.error("{} - no response (timeout)", prefix);
        } else if (response.getErrorCode() != null) {
            log.error("{} - error {}", prefix, response.getErrorCode());
        } else {
            log.error("{} - there is no error code in speaker response, can't provide any details regarding happen"
                              + " error", prefix);
        }
    }

    public void reportMalfunction(BfdPortFsmState from, BfdPortFsmState to, BfdPortFsmEvent event,
                                  BfdPortFsmContext context) {
        if (log.isErrorEnabled()) {
            log.error("{} is in FAIL state - ignore {} request", makeLogPrefix(), event);
        }
    }

    // -- private/service methods --

    private void doBfdSetup(BfdPortFsmContext context) {
        try {
            sessionDescriptor = allocateDiscriminator(makeSessionDescriptor(context.getIslReference()));
            logInfo(String.format("BFD session setup process have started - discriminator:%s, remote-datapath:%s",
                                  sessionDescriptor.getDiscriminator(), sessionDescriptor.getRemote().getDatapath()));

            pendingRequestKey = context.getOutput().setupBfdSession(makeBfdSessionRecord());
        } catch (SwitchReferenceLookupException e) {
            log.error("Can't make BFD-session setup request - {}", e.getMessage());
            fire(BfdPortFsmEvent.FAIL, context);
        }
    }

    private void doBfdRemove(BfdPortFsmContext context) {
        logInfo(String.format("perform BFD session remove - discriminator:%s, remote-datapath:%s",
                              sessionDescriptor.getDiscriminator(), sessionDescriptor.getRemote().getDatapath()));
        pendingRequestKey = context.getOutput().removeBfdSession(makeBfdSessionRecord());
    }

    private NoviBfdSession makeBfdSessionRecord() {
        return NoviBfdSession.builder()
                .target(sessionDescriptor.getLocal())
                .remote(sessionDescriptor.getRemote())
                .physicalPortNumber(physicalEndpoint.getPortNumber())
                .logicalPortNumber(logicalEndpoint.getPortNumber())
                .udpPortNumber(BFD_UDP_PORT)
                .discriminator(sessionDescriptor.getDiscriminator())
                .intervalMs(bfdPollInterval)
                .multiplier(bfdFailCycleLimit)
                .keepOverDisconnect(true)
                .build();
    }

    private BfdDescriptor allocateDiscriminator(BfdDescriptor descriptor) {
        BfdSession dbView = loadBfdSession()
                .orElseGet(() -> new BfdSession(logicalEndpoint.getDatapath(), logicalEndpoint.getPortNumber()));

        Integer discriminator = dbView.getDiscriminator();
        descriptor.fill(dbView);

        if (discriminator == null) {
            while (true) {
                // FIXME(surabujin): loop will never end if all possible discriminators are allocated
                discriminator = random.nextInt();
                try {
                    dbView.setDiscriminator(discriminator);
                    bfdSessionRepository.createOrUpdate(dbView);
                    break;
                } catch (ConstraintViolationException ex) {
                    log.warn("ConstraintViolationException on allocate bfd discriminator");
                }
            }
        } else {
            bfdSessionRepository.createOrUpdate(dbView);
        }

        return descriptor.toBuilder()
                .discriminator(discriminator)
                .build();
    }

    private Optional<BfdSession> loadBfdSession() {
        return bfdSessionRepository.findBySwitchIdAndPort(
                logicalEndpoint.getDatapath(), logicalEndpoint.getPortNumber());
    }

    private BfdDescriptor makeSessionDescriptor(IslReference islReference) throws SwitchReferenceLookupException {
        Endpoint remoteEndpoint = islReference.getOpposite(getPhysicalEndpoint());
        return BfdDescriptor.builder()
                .local(makeSwitchReference(physicalEndpoint.getDatapath()))
                .remote(makeSwitchReference(remoteEndpoint.getDatapath()))
                .build();
    }

    private SwitchReference makeSwitchReference(SwitchId datapath) throws SwitchReferenceLookupException {
        Optional<Switch> sw = switchRepository.findById(datapath);
        if (!sw.isPresent()) {
            throw new SwitchReferenceLookupException(datapath, "persistent record is missing");
        }
        return makeSwitchReference(datapath, sw.get().getAddress());
    }

    private SwitchReference makeSwitchReference(SwitchId datapath, String ipAddress)
            throws SwitchReferenceLookupException {
        InetAddress address;
        try {
            address = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            throw new SwitchReferenceLookupException(
                    datapath,
                    String.format("unable to parse switch address \"%s\"", ipAddress));
        }

        return new SwitchReference(datapath, address);
    }

    private void logInfo(String message) {
        if (log.isInfoEnabled()) {
            log.info("{} - {}", makeLogPrefix(), message);
        }
    }

    private String makeLogPrefix() {
        return String.format("BFD port %s(physical-port:%s)", logicalEndpoint, physicalEndpoint.getPortNumber());
    }

    private static class ResponseKeyValidator implements Condition<BfdPortFsmContext> {
        @Override
        public boolean isSatisfied(BfdPortFsmContext context) {
            BfdPortFsm fsm = context.getFsm();
            String requestKey = context.getRequestKey();
            if (requestKey != null) {
                return requestKey.equals(fsm.pendingRequestKey);
            }
            return false;
        }

        @Override
        public String name() {
            return "ensureResponseIsActual";
        }
    }

    @Value
    @Builder
    public static class BfdPortFsmContext {
        private final BfdPortFsm fsm;  // required for conditions to access FSM fields
        private final IBfdPortCarrier output;

        private IslReference islReference;

        private String requestKey;
        private BfdSessionResponse bfdSessionResponse;

        /**
         * Builder.
         */
        public static BfdPortFsmContextBuilder builder(BfdPortFsm fsm, IBfdPortCarrier carrier) {
            return builder(carrier)
                    .fsm(fsm);
        }

        public static BfdPortFsmContextBuilder builder(IBfdPortCarrier carrier) {
            return (new BfdPortFsmContextBuilder())
                    .output(carrier);
        }
    }

    public enum BfdPortFsmEvent {
        NEXT, KILL, FAIL,

        HISTORY,
        ENABLE, DISABLE,
        BI_ISL_MOVE,
        PORT_UP, PORT_DOWN,

        SPEAKER_SUCCESS, SPEAKER_FAIL,

        _INIT_CHOICE_CLEAN, _INIT_CHOICE_DIRTY,
        _CLEANING_CHOICE_READY, _CLEANING_CHOICE_HOLD
    }

    public enum BfdPortFsmState {
        INIT,
        INIT_CHOICE,
        IDLE,

        INSTALLING,
        ACTIVE, UP, DOWN,

        CLEANING,
        CLEANING_CHOICE,
        WAIT_RELEASE,
        HOUSEKEEPING,

        FAIL,
        STOP
    }
}
