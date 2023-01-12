install_apps:
	onos-app localhost install! apps/proxyarp-1.0-SNAPSHOT.oar
	onos-app localhost install! apps/bridge-1.0-SNAPSHOT.oar
	onos-app localhost install! apps/unicastdhcp-1.0-SNAPSHOT.oar

dhcp_server:
	sudo /usr/sbin/dhcpd 4 -pf /run/dhcp-server-dhcpd.pid -cf ./dhcp/dhcpd.conf vethdhcpovs1
