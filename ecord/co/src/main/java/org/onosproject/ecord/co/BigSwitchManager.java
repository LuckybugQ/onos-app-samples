/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.ecord.co;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.Device;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DefaultPortDescription;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortDescription;
import org.onosproject.net.edge.EdgePortEvent;
import org.onosproject.net.edge.EdgePortListener;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.AtomicCounter;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.slf4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Listens for edge and port changes in the underlying data path and
 * exposes a big switch abstraction.
 */
@Component(immediate = true)
@Service
public class BigSwitchManager
        extends AbstractListenerManager<BigSwitchEvent, BigSwitchListener>
        implements BigSwitchService {
    private static final Logger log = getLogger(BigSwitchDeviceProvider.class);

    private static final String PORT_MAP = "ecord-port-map";
    private static final String PORT_COUNTER = "ecord-port-counter";
    private static final Serializer SERIALIZER = Serializer.using(KryoNamespaces.API);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected EdgePortService edgePortService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    // Holds the physical port to virtual port number mapping
    private ConsistentMap<ConnectPoint, Long> portMap;
    // Counter for virtual port numbers
    private AtomicCounter portCounter;
    // TODO: Add other listeners once we decide what an edge really is
    private EdgePortListener edgeListener = new InternalEdgeListener();
    private DeviceListener deviceListener = new InternalDeviceListener();

    @Activate
    public void activate() {
        portMap = storageService.<ConnectPoint, Long>consistentMapBuilder()
                .withName(PORT_MAP)
                .withSerializer(SERIALIZER)
                .build();
        portCounter = storageService.atomicCounterBuilder()
                .withName(PORT_COUNTER)
                .withMeteringDisabled()
                .build();
        eventDispatcher.addSink(BigSwitchEvent.class, listenerRegistry);
        //portCounter.compareAndSet(0, 1);     // Start counting from 1, doesn't work??
        buildPorts();
        edgePortService.addListener(edgeListener);
        deviceService.addListener(deviceListener);
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        edgePortService.removeListener(edgeListener);
        deviceService.removeListener(deviceListener);
        log.info("Stopped");
    }

    @Override
    public List<PortDescription> getPorts() {
        return portMap.keySet().stream()
                .map(cp -> toPortDescription(cp))
                .collect(Collectors.toList());
    }

    /**
     * Convert connect point to port description.
     *
     * @param cp connect point
     * @return port description
     */
    private PortDescription toPortDescription(ConnectPoint cp) {
        Port p = deviceService.getPort(cp.deviceId(), cp.port());
        // This is annoying
        DefaultAnnotations.Builder annot = DefaultAnnotations.builder();
        p.annotations().keys()
                .forEach(k -> annot.set(k, p.annotations().value(k)));

        return new DefaultPortDescription(
                PortNumber.portNumber(portMap.get(cp).value()),
                p.isEnabled(),
                p.type(),
                p.portSpeed(),
                annot.build()
        );
    }

    private class InternalEdgeListener implements EdgePortListener {
        @Override
        public boolean isRelevant(EdgePortEvent event) {
            // Only listen for real devices
            Device d = deviceService.getDevice(event.subject().deviceId());

            return !d.type().equals(Device.Type.VIRTUAL);
        }

        @Override
        public void event(EdgePortEvent event) {
            log.debug("Edge event {} {}", event.subject(), event.type());
            BigSwitchEvent.Type bigSwitchEvent;

            switch (event.type()) {
                case EDGE_PORT_ADDED:
                    portMap.put(event.subject(), portCounter.getAndIncrement());
                    bigSwitchEvent = BigSwitchEvent.Type.PORT_ADDED;
                    break;
                case EDGE_PORT_REMOVED:
                    portMap.remove(event.subject());
                    bigSwitchEvent = BigSwitchEvent.Type.PORT_REMOVED;
                    break;
                default:
                    return;
            }

            post(new BigSwitchEvent(bigSwitchEvent, toPortDescription(event.subject())));
        }
    }

    private class InternalDeviceListener implements DeviceListener {
        @Override
        public boolean isRelevant(DeviceEvent event) {
            // Only listen for real devices
            return !event.subject().type().equals(Device.Type.VIRTUAL);
        }

        @Override
        public void event(DeviceEvent event) {
            log.debug("Device event {} {} {}", event.subject(), event.port(), event.type());

            switch (event.type()) {
                // Ignore most of these for now
                case DEVICE_ADDED:
                case DEVICE_AVAILABILITY_CHANGED:
                case DEVICE_REMOVED:
                case DEVICE_SUSPENDED:
                case DEVICE_UPDATED:
                case PORT_ADDED:
                case PORT_REMOVED:
                case PORT_STATS_UPDATED:
                    break;
                case PORT_UPDATED:
                    // Update if state of existing edge changed
                    ConnectPoint cp = new ConnectPoint(event.subject().id(), event.port().number());
                    if (portMap.containsKey(cp)) {
                        post(new BigSwitchEvent(BigSwitchEvent.Type.PORT_UPDATED, toPortDescription(cp)));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void buildPorts() {
        edgePortService.getEdgePoints()
                .forEach(cp -> portMap.put(cp, portCounter.getAndIncrement()));
    }
}
