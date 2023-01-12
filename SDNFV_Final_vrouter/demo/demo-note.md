## Setup
onos-netcfg localhost ~/SDNFV_Projects/SDNFV_Project7/demo/config.json
onos-app localhost install! ~/SDNFV_Projects/SDNFV_Project7/vrouter/target/vrouter-1.0-SNAPSHOT.oar
make install_apps
make dhcp_server

## Transit Traffic
sudo docker exec h03 ping -c5 172.30.2.2
sudo docker exec h03 ping -c5 172.30.3.2

## Internal Traffic
sudo docker exec h01 ping -c5 172.30.0.2

## External Traffic

- Outgoing
sudo docker exec h01 ping -c5 172.30.1.2
sudo docker exec h01 ping -c5 172.30.2.2
sudo docker exec h01 ping -c5 172.30.3.2
sudo docker exec h01 ping -c5 172.30.4.2
sudo docker exec h02 ping -c5 172.30.1.2
sudo docker exec h02 ping -c5 172.30.2.2
sudo docker exec h02 ping -c5 172.30.3.2
sudo docker exec h02 ping -c5 172.30.4.2

- Incoming
sudo docker exec h03 ping -c5 172.30.0.1
sudo docker exec h03 ping -c5 172.30.0.2
sudo docker exec h04 ping -c5 172.30.0.1
sudo docker exec h04 ping -c5 172.30.0.2
sudo docker exec h05 ping -c5 172.30.0.1
sudo docker exec h05 ping -c5 172.30.0.2
sudo docker exec h06 ping -c5 172.30.0.1
sudo docker exec h06 ping -c5 172.30.0.2

## DHCP 
sudo docker exec h07 dhclient -v
sudo docker exec h03 ping -c5 172.30.0.100
sudo docker exec h07 ping -c5 172.30.1.2