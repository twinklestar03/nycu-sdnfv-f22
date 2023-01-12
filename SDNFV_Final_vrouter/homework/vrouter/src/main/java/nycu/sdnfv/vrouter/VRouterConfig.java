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

import java.util.List;

import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.config.Config;

import com.fasterxml.jackson.databind.ser.std.StdKeySerializers.Default;

public class VRouterConfig extends Config<ApplicationId> {

    public static final String QUAGGA_CP = "quagga";
	public static final String QUAGGA_MAC = "quagga-mac";
	public static final String VIRTUAL_IP = "virtual-ip";
	public static final String VIRTUAL_MAC = "virtual-mac";
	public static final String PEERS = "peers";

    @Override
    public boolean isValid() {
        return isMacAddress(QUAGGA_MAC, FieldPresence.MANDATORY) 
			&& isConnectPoint(QUAGGA_CP, FieldPresence.MANDATORY)
			&& isIpAddress(VIRTUAL_IP, FieldPresence.MANDATORY)
			&& isMacAddress(VIRTUAL_MAC, FieldPresence.MANDATORY);
    }

    /**
	 * Returns the device connect point
	 * 
	 * @return connect point or null if not set
	 */
	public ConnectPoint routerConnectPoint() {
		String connectPoint = get(QUAGGA_CP, null);
		return connectPoint != null ? ConnectPoint.deviceConnectPoint(connectPoint) : null;
	}

	public MacAddress routerMacAddress() {
		String macAddressStr = get(QUAGGA_MAC, null);
		return macAddressStr != null ? MacAddress.valueOf(macAddressStr) : null;
	}

	public IpAddress virtualIpAddress() {
		String ipString = get(VIRTUAL_IP, null);
		return ipString != null ? IpAddress.valueOf(ipString) : null;
	}

	public MacAddress virtualMacAddress() {
		String macString = get(VIRTUAL_MAC, null);
		return macString != null ? MacAddress.valueOf(macString) : null;
	}

	public List<IpAddress> peerAddresses() {
		return getList(PEERS, IpAddress::valueOf, null);
	}
}
