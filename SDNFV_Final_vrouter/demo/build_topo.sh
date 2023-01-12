#!/bin/bash
#set -x

if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

apt install bridge-utils

routerImage="quagga-fpm"
hostImage="host-mano"
BASEDIR=$(dirname "$0")

# params: endpoint1 endpoint2
function create_veth_pair {
    ip link add $1 type veth peer name $2
    ip link set $1 up
    ip link set $2 up
}

# params: image_name container_name
function add_container {
	docker run -di --network=none --privileged --cap-add NET_ADMIN --cap-add SYS_MODULE \
		 --hostname $2 --name $2 ${@:3} $1
	pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=$2"))
	mkdir -p /var/run/netns
	ln -s /proc/$pid/ns/net /var/run/netns/$pid
}

# params: container_name infname [ipaddress] [gw addr]
function set_intf_container {
    pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=$1"))
    ifname=$2
    ipaddr=$3
    echo "Add interface $ifname with ip $ipaddr to container $1"

    ip link set "$ifname" netns "$pid"
    if [ $# -ge 3 ]
    then
        ip netns exec "$pid" ip addr add "$ipaddr" dev "$ifname"
    fi
    ip netns exec "$pid" ip link set "$ifname" up
    if [ $# -ge 4 ]
    then
        ip netns exec "$pid" route add default gw $4
    fi
}

# params: bridge_name container_name [ipaddress] [gw addr]
function build_bridge_container_path {
    br_inf="veth$1$2"
    container_inf="veth$2$1"
    create_veth_pair $br_inf $container_inf
    brctl addif $1 $br_inf
    set_intf_container $2 $container_inf $3 $4
}

# params: ovs1 ovs2
function build_ovs_path {
    inf1="veth$1$2"
    inf2="veth$2$1"
    create_veth_pair $inf1 $inf2
    ovs-vsctl add-port $1 $inf1
    ovs-vsctl add-port $2 $inf2
}

# params: ovs container [ipaddress] [gw addr]
function build_ovs_container_path {
    ovs_inf="veth$1$2"
    container_inf="veth$2$1"
    create_veth_pair $ovs_inf $container_inf
    ovs-vsctl add-port $1 $ovs_inf
    set_intf_container $2 $container_inf $3 $4
}

iptables -P FORWARD ACCEPT

add_container $hostImage h01
add_container $hostImage h02
add_container $hostImage h03
add_container $hostImage h04
add_container $hostImage h05
add_container $hostImage h06
add_container $hostImage h07
add_container $routerImage er1 -v $(realpath $BASEDIR/bgp_confs/er1.conf):/etc/quagga/bgpd.conf -v $(realpath $BASEDIR/bgp_confs/zebra_er1.conf):/etc/quagga/zebra.conf
add_container $routerImage er2 -v $(realpath $BASEDIR/bgp_confs/er2.conf):/etc/quagga/bgpd.conf -v $(realpath $BASEDIR/bgp_confs/zebra_er2.conf):/etc/quagga/zebra.conf
add_container $routerImage er3 -v $(realpath $BASEDIR/bgp_confs/er3.conf):/etc/quagga/bgpd.conf -v $(realpath $BASEDIR/bgp_confs/zebra_er3.conf):/etc/quagga/zebra.conf
add_container $routerImage er4 -v $(realpath $BASEDIR/bgp_confs/er4.conf):/etc/quagga/bgpd.conf -v $(realpath $BASEDIR/bgp_confs/zebra_er4.conf):/etc/quagga/zebra.conf
add_container $routerImage speaker -v $(realpath $BASEDIR/bgp_confs/speaker.conf):/etc/quagga/bgpd.conf -v $(realpath $BASEDIR/bgp_confs/zebra_speaker.conf):/etc/quagga/zebra.conf

ovs-vsctl add-br ovs1 -- set bridge ovs1 other_config:datapath-id=0000000000000001 -- set bridge ovs1 protocols=OpenFlow14 -- set-controller ovs1 tcp:127.0.0.1:6633
ovs-vsctl add-br ovs2 -- set bridge ovs2 other_config:datapath-id=0000000000000002 -- set bridge ovs2 protocols=OpenFlow14 -- set-controller ovs2 tcp:127.0.0.1:6633
ovs-vsctl add-br ovs3 -- set bridge ovs3 other_config:datapath-id=0000000000000003 -- set bridge ovs3 protocols=OpenFlow14 -- set-controller ovs3 tcp:127.0.0.1:6633
ip l set ovs1 up
ip l set ovs2 up
ip l set ovs3 up

brctl addbr bre1
brctl addbr bre2
brctl addbr bre3
brctl addbr bre4
brctl addbr bree
ip l set bre1 up
ip l set bre2 up
ip l set bre3 up
ip l set bre4 up
ip l set bree up

# 65000
build_ovs_path ovs1 ovs2
build_ovs_path ovs1 ovs3

build_ovs_container_path ovs2 h01 172.30.0.1/24 172.30.0.254
build_ovs_container_path ovs2 h02 172.30.0.2/24 172.30.0.254
build_ovs_container_path ovs1 speaker

#65001
build_bridge_container_path bre1 h03 172.30.1.2/24 172.30.1.1
build_bridge_container_path bre1 er1 172.30.1.1/24

#65002
build_bridge_container_path bre2 h04 172.30.2.2/24 172.30.2.1
build_bridge_container_path bre2 er2 172.30.2.1/24

#65003
build_bridge_container_path bre3 h05 172.30.3.2/24 172.30.3.1
build_bridge_container_path bre3 er3 172.30.3.1/24

#65004
build_bridge_container_path bre4 h06 172.30.4.2/24 172.30.4.1
build_bridge_container_path bre4 er4 172.30.4.1/24

build_ovs_container_path ovs1 er1 10.28.3.2/24
build_ovs_container_path ovs3 er2 10.76.5.2/24
build_ovs_container_path ovs2 er3 10.96.1.2/24
build_ovs_container_path ovs2 er4 10.34.6.2/24

# speaker
create_veth_pair vethhostspeaker vethspeakerhost
ip a add 172.29.1.1/24 dev vethhostspeaker
set_intf_container speaker vethspeakerhost 172.29.1.2/24

docker exec -it speaker ip a add 10.28.3.1/24 dev vethspeakerovs1
docker exec -it speaker ip a add 10.76.5.1/24 dev vethspeakerovs1
docker exec -it speaker ip a add 10.96.1.1/24 dev vethspeakerovs1
docker exec -it speaker ip a add 10.34.6.1/24 dev vethspeakerovs1

# notice me, ONOS senpai
docker exec -it h01 ping -c 1 8.8.8.8
docker exec -it h02 ping -c 1 8.8.8.8
docker exec -it h03 ping -c 1 8.8.8.8
docker exec -it h04 ping -c 1 8.8.8.8
docker exec -it h05 ping -c 1 8.8.8.8
docker exec -it h06 ping -c 1 8.8.8.8
docker exec -it h07 ping -c 1 8.8.8.8

docker exec -it er1 ping -c 1 10.28.3.1
docker exec -it er2 ping -c 1 10.76.5.1
docker exec -it er3 ping -c 1 10.96.1.1
docker exec -it er4 ping -c 1 10.34.6.1

create_veth_pair vethdhcpovs1 vethovs1dhcp
ovs-vsctl add-port ovs1 vethovs1dhcp
ip a add 172.30.0.240/24 dev vethdhcpovs1
build_ovs_container_path ovs1 h07
