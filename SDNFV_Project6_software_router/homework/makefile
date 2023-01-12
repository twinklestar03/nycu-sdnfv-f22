target: all


all: containers ovs

containers:
	docker compose up -d --build

ovs: containers
	ovs-vsctl add-br br3
	ovs-docker add-port br3 eth1 R1 --ipaddress=172.20.0.2/16
	ovs-docker add-port br3 eth1 R2 --ipaddress=172.20.0.3/16
	ovs-docker add-port br3 eth1 R3 --ipaddress=172.20.0.4/16

clean:
	docker compose down
	ovs-vsctl del-br br3