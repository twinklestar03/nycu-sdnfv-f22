#!/bin/bash
if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

for c in h01 h02 h03 h04 h05 h06 er1 er2 er3 speaker; do
    docker kill --signal=9 $c
    docker rm $c
done

ovs-vsctl del-br ovs1
ovs-vsctl del-br ovs2

ip l del bre1
ip l del bre2
ip l del bre3
ip l del bree

basename -a /sys/class/net/* | grep veth | xargs -I '{}' ip l del {}
