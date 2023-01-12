#!/bin/bash

sudo apt update && sudo apt install -y isc-dhcp-server

sudo ln -s /etc/apparmor.d/usr.sbin.dhcpd etc/apparmor.d/disable/
sudo apparmor_parser -R /etc/apparmor.d/usr.sbin.dhcpd