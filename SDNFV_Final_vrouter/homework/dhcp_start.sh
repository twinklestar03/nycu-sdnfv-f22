#!/bin/bash

sudo /usr/sbin/dhcpd 4 -pf /run/dhcp-server-dhcpd.pid -cf ./dhcpd.conf vethdhcpsovs1
