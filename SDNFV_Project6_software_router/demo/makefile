target: all


all: containers ovs

containers:
	docker compose up -d --build

ovs: containers
	ovs-vsctl add-br br3
	ovs-vsctl add-br br4
	ovs-docker add-port br3 eth1 R1 --ipaddress=172.18.0.2/16
	ovs-docker add-port br3 eth1 h1 --ipaddress=172.18.0.3/16
	ovs-docker add-port br4 eth1 R2 --ipaddress=172.19.0.2/16
	ovs-docker add-port br4 eth1 h2 --ipaddress=172.19.0.3/16
	docker exec h1 ip route add default via 172.18.0.2
	docker exec h2 ip route add default via 172.19.0.2

clean:
	docker compose down
	ovs-vsctl del-br br3
	ovs-vsctl del-br br4