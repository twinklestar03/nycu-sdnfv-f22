/*
 * Copyright 2022-present Open Networking Foundation
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
package nctu.winlab.bridge;

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import java.util.Dictionary;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.onlab.util.Tools.get;


/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {LearningBridge.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class LearningBridge {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    protected Map<DeviceId, Map<MacAddress, PortNumber>> macTables = Maps.newConcurrentMap();
    private ApplicationId appId;
    private PacketProcessor processor;

    // Useless property, hope it can make onos stop complaining.
    private String someProperty;

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        appId = coreService.getAppId("nctu.winlab.bridge"); //equal to the name shown in pom.xml file

        processor = new BridgePacketProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(3));

        /*
         * Restricts packet types to IPV4 and ARP by only requesting those types.
         */
        packetService.requestPackets(
            DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
            PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(
            DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(),
            PacketPriority.REACTIVE, appId, Optional.empty());
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        packetService.removeProcessor(processor);
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    private class BridgePacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            initTableEntry(context.inPacket().receivedFrom());
            short ethType = context.inPacket().parsed().getEtherType();
            if (ethType != Ethernet.TYPE_IPV4 && ethType != Ethernet.TYPE_ARP) {
                return;
            }

            ConnectPoint connectPoint = context.inPacket().receivedFrom();
            Map<MacAddress, PortNumber> macTable = macTables.get(connectPoint.deviceId());
            MacAddress srcMac = context.inPacket().parsed().getSourceMAC();
            MacAddress dstMac = context.inPacket().parsed().getDestinationMAC();
            if (!macTable.containsKey(srcMac)) {
                logEntryAdded(srcMac, connectPoint.deviceId(), connectPoint.port());
                macTable.put(srcMac, connectPoint.port());
            }

            PortNumber outPort = macTable.get(dstMac);
            if (outPort == null) {
                // We didn't found the out port, flood the packet.
                logEntryMissed(dstMac, connectPoint.deviceId());
                context.treatmentBuilder().setOutput(PortNumber.FLOOD);
                context.send();
                return;
            }

            // We send the packet to specific port
            logEntryMatched(dstMac, connectPoint.deviceId());
            context.treatmentBuilder().setOutput(outPort);
            FlowRule rule = DefaultFlowRule.builder()
                .withSelector(
                    DefaultTrafficSelector.builder()
                    .matchEthSrc(srcMac)
                    .matchEthDst(dstMac)
                    .build())
                .withTreatment(DefaultTrafficTreatment.builder().setOutput(outPort).build())
                .forDevice(connectPoint.deviceId())
                .withPriority(30)
                .makeTemporary(30)
                .fromApp(appId)
                .build();

            flowRuleService.applyFlowRules(rule);
            context.send();
            return;
        }

        private void logEntryAdded(MacAddress mac, DeviceId id, PortNumber port) {
            log.info(
                String.format("Add an entry to the port table of `%s`. MAC address: `%s` => Port: `%s`.",
                id.toString(),
                mac.toString(),
                port.toString())
            );
        }


        private void logEntryMissed(MacAddress mac, DeviceId id) {
            log.info(
                String.format("MAC address `%s` is missed on `%s`. Flood the packet.", mac.toString(), id.toString())
            );
        }

        private void logEntryMatched(MacAddress mac, DeviceId id) {
            log.info(
                String.format("MAC address `%s` is matched on `%s`. Install a flow rule.",
                mac.toString(),
                id.toString())
            );
        }

        private void initTableEntry(ConnectPoint cp) {
            macTables.putIfAbsent(cp.deviceId(), Maps.newConcurrentMap());
        }

    }

}
