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
package nycu.sdnfv.vrouter;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.onlab.packet.DHCP;
import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
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
import org.onosproject.net.Host;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.routeservice.ResolvedRoute;
import org.onosproject.routeservice.RouteService;
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

    private final ConfigFactory<ApplicationId, VRouterConfig> factory = new ConfigFactory<ApplicationId, VRouterConfig>(
        APP_SUBJECT_FACTORY, VRouterConfig.class, "router") {
        @Override
        public VRouterConfig createConfig() {
            return new VRouterConfig();
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

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected RouteService routeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected InterfaceService intfService;

    private ApplicationId appId;

    private PacketProcessor processor;

    private ConnectPoint routerCp;
    private MacAddress routerMac;
    private IpAddress virtualIp;
    private MacAddress virtualMac;
    private List<IpAddress> peers;

    private List<MacAddress> installedMacs = new ArrayList<>();
    private List<Intent> installedIntents = new ArrayList<>(); 

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.sdnfv.vrouter");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);

        // Initialize a packet processor
        processor = new VRouterProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(6));

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
        // TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
        //     .matchEthType(Ethernet.TYPE_IPV4);
        // packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
                PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(),
                PacketPriority.REACTIVE, appId, Optional.empty());

    }

    private void cancelPackets() {
        // TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
        //     .matchEthType(Ethernet.TYPE_IPV4);
        // packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
                PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(),
                PacketPriority.REACTIVE, appId, Optional.empty());
    }

    private class VRouterProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }

            Ethernet ethPkt = context.inPacket().parsed();
            if (ethPkt == null) {
                return;
            }
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();
            log.info("[L2] srcMac: {}, dstMac: {}", srcMac, dstMac);

            if (ethPkt.getEtherType() != Ethernet.TYPE_IPV4) {
                return;
            }

            IPv4 ipPkt = (IPv4) ethPkt.getPayload();
            IpAddress srcIp = IpAddress.valueOf(ipPkt.getSourceAddress());
            IpAddress dstIp = IpAddress.valueOf(ipPkt.getDestinationAddress());
            log.info("[L3] srcMac: {}, dstMac: {}, srcIp: {}, dstIp: {}", srcMac, dstMac, srcIp, dstIp);
            
            // If the dstIp is a known host. We do L2 modification for inbound packets
            MacAddress hostMac = hostService.getHostsByIp(dstIp).stream()
                .map(Host::mac)
                .findFirst()
                .orElse(null);
            ConnectPoint hostCp = hostService.getHostsByIp(dstIp).stream()
                .map(Host::location)
                .findFirst()
                .orElse(null);
            
            if (hostMac != null) {
                log.info("[External->SDN] HostMac is found. L2 modification is needed. HostMac: {}", hostMac);
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setEthSrc(virtualMac)
                    .setEthDst(hostMac)
                    .build();
                
                TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPDst(dstIp.toIpPrefix())
                    .build();
                
                PointToPointIntent intent = PointToPointIntent.builder()
                    .appId(appId)
                    .selector(selector)
                    .treatment(treatment)
                    .filteredIngressPoint(new FilteredConnectPoint(context.inPacket().receivedFrom()))
                    .filteredEgressPoint(new FilteredConnectPoint(hostCp))
                    .build();

                intentService.submit(intent);
                installedIntents.add(intent);

                context.block();
                return;
            } else {
                log.info("[External->SDN] HostMac is not found. Next...");
            }

            // L2 modification for outbound packets
            // check dstIp is out of the router subnet according to subnet mask
            Optional<ResolvedRoute> route = routeService.longestPrefixLookup(dstIp);
            if (route.isPresent()) {
                log.info("[SDN->External] Route is found. L2 modification is needed. NextHop: {}", route.get().nextHop());
                MacAddress nextHopMac = hostService.getHostsByIp(route.get().nextHop().getIp4Address()).stream()
                    .map(Host::mac)
                    .findFirst()
                    .orElse(null);
                ConnectPoint egressPoint = intfService.getMatchingInterface(
                    route.get().nextHop().getIp4Address()).connectPoint();

                if (nextHopMac == null) {
                    log.info("[SDN->External] NextHopMac is not found. Ignore the packet.");
                    return;
                }
                
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setEthSrc(routerMac)
                    .setEthDst(nextHopMac)
                    .build();
                log.info("[SDN->External] srcEth: {}, dstEth: {}", routerMac, nextHopMac);

                TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPSrc(srcIp.toIpPrefix())
                    .matchIPDst(dstIp.toIpPrefix())
                    .build();

                PointToPointIntent intent = PointToPointIntent.builder()
                    .appId(appId)
                    .selector(selector)
                    .treatment(treatment)
                    .filteredIngressPoint(new FilteredConnectPoint(context.inPacket().receivedFrom()))
                    .filteredEgressPoint(new FilteredConnectPoint(egressPoint))
                    .build();
                
                log.info("[SDN->External] Intent install for L2 modification. Intent: {}", intent);
                
                intentService.submit(intent);
                installedIntents.add(intent);

                context.block();
                return;
            } else {
                log.info("[SDN->External] Route is not found. Ignore the packet.");
                return;
            }
        }

    }

    private class NameConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                && event.configClass().equals(VRouterConfig.class)) {
                    VRouterConfig config = cfgService.getConfig(appId, VRouterConfig.class);
                if (config != null) {
                    // drop all existing flows/intents
                    for (Intent intent : installedIntents) {
                        intentService.withdraw(intent);
                    }

                    routerCp = config.routerConnectPoint();
                    routerMac = config.routerMacAddress();
                    virtualIp = config.virtualIpAddress();
                    virtualMac = config.virtualMacAddress();
                    peers = config.peerAddresses();

                    log.info("Router Connect Point: {}", routerCp);
                    log.info("Router MAC Address: {}", routerMac);
                    log.info("Virtual IP Address: {}", virtualIp);
                    log.info("Virtual MAC Address: {}", virtualMac);
                    log.info("Peers: {}", peers);

                    log.info("Setting up Outgoing/Incoming eBGP flow-rule");
                    for (IpAddress peerAddress : peers) {
                        ConnectPoint interfaceCp = intfService.getMatchingInterface(peerAddress).connectPoint();
                        IpAddress interfaceIp = intfService.getInterfacesByPort(interfaceCp).stream()
                            .map(Interface::ipAddressesList)
                            .flatMap(List::stream)
                            .findFirst()
                            .orElse(null)
                            .ipAddress();
                        
                        if (interfaceCp == null) {
                            log.warn("No interface found for peer {}", interfaceCp);
                            continue;
                        }

                        log.info("Setting up Outgoing eBGP flow-rule");
                        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                            .matchEthType(Ethernet.TYPE_IPV4)
                            .matchIPDst(IpPrefix.valueOf(peerAddress, 32));

                        PointToPointIntent outgoingIntent = PointToPointIntent.builder()
                            .appId(appId)
                            .selector(selector.build())
                            .filteredEgressPoint(new FilteredConnectPoint(interfaceCp))
                            .filteredIngressPoint(new FilteredConnectPoint(routerCp))
                            .build();
                        
                        log.info("Setting up Incoming eBGP flow-rule");
                        selector = DefaultTrafficSelector.builder()
                            .matchEthType(Ethernet.TYPE_IPV4)
                            .matchIPDst(IpPrefix.valueOf(interfaceIp, 32));

                        PointToPointIntent incomingIntent = PointToPointIntent.builder()
                            .appId(appId)
                            .selector(selector.build())
                            .filteredEgressPoint(new FilteredConnectPoint(routerCp))
                            .filteredIngressPoint(new FilteredConnectPoint(interfaceCp))
                            .build();
                        
                        intentService.submit(outgoingIntent);
                        intentService.submit(incomingIntent);
                        installedIntents.add(outgoingIntent);
                        installedIntents.add(incomingIntent);

                        log.info("Added Outgoing/Incoming eBGP flow-rule for interface: {}", interfaceCp);
                    }
                }
            }
        }
    }
}
