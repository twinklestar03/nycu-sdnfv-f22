
## Install
```
onos-app localhost install! ~/SDNFV_Projects/SDNFV_Project7/target/bridge-1.0-SNAPSHOT.oar
onos-app localhost install! ~/SDNFV_Projects/SDNFV_Project7/target/proxyarp-1.0-SNAPSHOT.oar
onos-app localhost install! ~/SDNFV_Projects/SDNFV_Project7/target/unicastdhcp-1.0-SNAPSHOT.oar
onos-app localhost install! ~/SDNFV_Projects/SDNFV_Project7/homework/target/vrouter-1.0-SNAPSHOT.oar

onos-netcfg localhost ~/SDNFV_Projects/SDNFV_Project7/config.json
```
## Uninstall
```
onos-app localhost uninstall nycu.sdnfv.vrouter
onos-app localhost uninstall nycu.sdnfv.proxyarp
onos-app localhost uninstall nycu.sdnfv.unicastdhcp
onos-app localhost uninstall nycu.sdnfv.bridge
```

## Reinstall
```
onos-app localhost uninstall nycu.sdnfv.vrouter && onos-app localhost install! ~/SDNFV_Projects/SDNFV_Project7/homework/target/vrouter-1.0-SNAPSHOT.oar

onos-netcfg localhost ~/SDNFV_Projects/SDNFV_Project7/config.json
```

## Check App
- Use ONOS CLI to show routing table and check rules for eBGP traffic
- Host 1 pings Host 2 (Intra domain traffic)
- Host 1 pings Host 3 (Inter domain traffic)
- Host 3 pings Host 4/5 (Transit traffic)
- Host 6 can obtain DHCP offer and ping Host 5 (DHCP + Inter domain traffic)