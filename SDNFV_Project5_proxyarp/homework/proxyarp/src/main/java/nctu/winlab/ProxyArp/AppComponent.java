/*
 * Copyright 2020-present Open Networking Foundation
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
package nctu.winlab.ProxyArp;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.onlab.packet.ARP;
import org.onlab.packet.DHCP;
import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import shaded.org.apache.maven.model.Build;

/** Sample Network Configuration Service Application. **/
@Component(immediate = true)
public class AppComponent {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    private ApplicationId appId;

    private PacketProcessor processor;

    private Map<Ip4Address, MacAddress> macTable = new HashMap<>();
    private Map<Ip4Address, ConnectPoint> connectPortTable = new HashMap<>();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.ProxyArp");
        // Initialize a packet processor
        processor = new ProxyArpProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(3));

        // Request ARP packets.
        this.requestPackets();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
        this.cancelPackets();

        log.info("Stopped");
    }

    private void requestPackets() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.empty());
    }

    private void cancelPackets() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.empty());
    }

    private class ProxyArpProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            Ethernet packet = context.inPacket().parsed();
            if (packet == null) {
                return;
            }

            if (packet.getEtherType() != Ethernet.TYPE_ARP) {
                return;
            }

            ARP arpPacket = (ARP) packet.getPayload();
            ConnectPoint fromPoint = context.inPacket().receivedFrom();
            MacAddress senderMac = MacAddress.valueOf(arpPacket.getSenderHardwareAddress());
            MacAddress receiverMac = MacAddress.valueOf(arpPacket.getTargetHardwareAddress());
            Ip4Address senderIpv4 = Ip4Address.valueOf(arpPacket.getSenderProtocolAddress());
            Ip4Address targetIpv4 = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());
            if (arpPacket.getOpCode() == ARP.OP_REPLY) {
                if (macTable.containsKey(senderIpv4)) {
                    return;
                }

                log.info("RECV REPLY. Requested MAC: {}", receiverMac.toString());
                macTable.put(senderIpv4, senderMac);
                connectPortTable.put(senderIpv4, fromPoint);
                
                TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder()
                    .setOutput(connectPortTable.get(targetIpv4).port());
                packetService.emit(new DefaultOutboundPacket(
                    connectPortTable.get(targetIpv4).deviceId(), treatmentBuilder.build(), context.outPacket().data()));

                return;
            } else if (arpPacket.getOpCode() == ARP.OP_REQUEST) {
                if (!macTable.containsKey(targetIpv4)) {
                    macTable.put(senderIpv4, senderMac);
                    connectPortTable.put(senderIpv4, fromPoint);
                    log.info("TABLE MISSED. SEND request to edge ports.");
                    edgePortService.emitPacket(context.outPacket().data(), Optional.empty());
                } else {     
                    log.info("TABLE HIT. Requested MAC: {}", senderMac.toString());
                    Ethernet arpReply = ARP.buildArpReply(targetIpv4, macTable.get(targetIpv4), packet);
                    TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder()
                        .setOutput(fromPoint.port());
                    packetService.emit(new DefaultOutboundPacket(
                        fromPoint.deviceId(), treatmentBuilder.build(), ByteBuffer.wrap(arpReply.serialize())));
                }
                return;
            }
            return;
        }
    }
}
