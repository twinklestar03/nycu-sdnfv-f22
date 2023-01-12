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
package nctu.winlab.unicastdhcp;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.onlab.packet.DHCP;
import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onlab.packet.DHCP.MsgType;
import org.onlab.packet.dhcp.DhcpOption;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PointToPointIntent;
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

    private final NameConfigListener cfgListener = new NameConfigListener();

    private final ConfigFactory<ApplicationId, NameConfig> factory = new ConfigFactory<ApplicationId, NameConfig>(
        APP_SUBJECT_FACTORY, NameConfig.class, "UnicastDhcpConfig") {
        @Override
        public NameConfig createConfig() {
            return new NameConfig();
        }
    };

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    private ApplicationId appId;

    private PacketProcessor processor;

    private ConnectPoint dhcpServer;

    private List<MacAddress> installedMacs = new ArrayList<>();
    private List<Intent> installedIntents = new ArrayList<>(); 

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.unicastdhcp");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);

        // Initialize a packet processor
        processor = new UnicastDhcpProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(3));

        // Request DHCP packets
        this.requestPackets();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);
        packetService.removeProcessor(processor);
        this.cancelPackets();

        for (Intent intent : installedIntents) {
            intentService.withdraw(intent);
        }

        log.info("Stopped");
    }

    private void requestPackets() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.empty());
    }

    private void cancelPackets() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.empty());
    }

    private class NameConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                && event.configClass().equals(NameConfig.class)) {
                NameConfig config = cfgService.getConfig(appId, NameConfig.class);
                if (config != null) {
                    dhcpServer = config.devicePoint();
                    log.info("DHCP server is connected to `{}`, port `{}`", 
                        dhcpServer.deviceId().toString(), dhcpServer.port().toString());
                }
            }
        }
    }

    private class UnicastDhcpProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            Ethernet packet = context.inPacket().parsed();
            if (packet == null) {
                return;
            }

            if (packet.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipv4Packet = (IPv4) packet.getPayload();
                log.info("Packet Received: {}", context.inPacket().receivedFrom().toString());
                if (ipv4Packet.getProtocol() == IPv4.PROTOCOL_UDP) {
                    UDP udpPacket = (UDP) ipv4Packet.getPayload();

                    if (udpPacket.getDestinationPort() == UDP.DHCP_SERVER_PORT &&
                            udpPacket.getSourcePort() == UDP.DHCP_CLIENT_PORT) {
                        // This is meant for the dhcp server so process the packet here.

                        DHCP dhcpPayload = (DHCP) udpPacket.getPayload();
                        this.processDhcpPacket(context, dhcpPayload);
                    }
                }
            }
            return;
        }

        private void processDhcpPacket(PacketContext context, DHCP dhcpPayload) {
            if (dhcpPayload == null) {
                return;
            }
            
            DHCP.MsgType incomingPacketType = null;
            for (DhcpOption option : dhcpPayload.getOptions()) {
                if (option.getCode() == DHCP.DHCPOptionCode.OptionCode_MessageType.getValue()) {
                    byte[] data = option.getData();
                    incomingPacketType = DHCP.MsgType.getType(data[0]);
                }
            }

            if (incomingPacketType != MsgType.DHCPDISCOVER && incomingPacketType != MsgType.DHCPREQUEST) {
                return;
            }

            if (installedMacs.contains(context.inPacket().parsed().getSourceMAC())) {
                log.info("Ignoring in-packets for already installed intents.");
                return;
            }
            installedMacs.add(context.inPacket().parsed().getSourceMAC());

            TrafficSelector.Builder clientSelector =
                DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPProtocol(IPv4.PROTOCOL_UDP)
                    .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
                    .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT));
            
            TrafficSelector.Builder serverSelector =
                DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPProtocol(IPv4.PROTOCOL_UDP)
                    .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
                    .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));

            ConnectPoint fromPoint = context.inPacket().receivedFrom();
            PointToPointIntent clientIntent = PointToPointIntent.builder()
                .appId(appId)
                .priority(10)
                .selector(clientSelector.build())
                .filteredIngressPoint(
                    new FilteredConnectPoint(fromPoint))
                .filteredEgressPoint(
                    new FilteredConnectPoint(dhcpServer))
                .build();
            intentService.submit(clientIntent);

            PointToPointIntent serverIntent = PointToPointIntent.builder()
                .appId(appId)
                .priority(10)
                .selector(serverSelector.build())
                .filteredIngressPoint(
                    new FilteredConnectPoint(dhcpServer))
                .filteredEgressPoint(
                    new FilteredConnectPoint(fromPoint))
                .build();
            intentService.submit(serverIntent);

            installedIntents.add(serverIntent);
            installedIntents.add(clientIntent);

            log.info(
                "Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                fromPoint.deviceId().toString(),
                fromPoint.port().toString(),
                dhcpServer.deviceId().toString(),
                dhcpServer.port().toString());

            log.info(
                "Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                dhcpServer.deviceId().toString(),
                dhcpServer.port().toString(),
                fromPoint.deviceId().toString(),
                fromPoint.port().toString());
        }
    }
}
