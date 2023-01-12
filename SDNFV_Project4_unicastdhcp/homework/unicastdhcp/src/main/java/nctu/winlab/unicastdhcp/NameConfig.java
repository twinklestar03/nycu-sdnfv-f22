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
package nctu.winlab.unicastdhcp;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.config.Config;

import com.fasterxml.jackson.databind.ser.std.StdKeySerializers.Default;

public class NameConfig extends Config<ApplicationId> {

    public static final String DEVICE_CONNECTPOINT = "serverLocation";

    @Override
    public boolean isValid() {
        return hasOnlyFields(DEVICE_CONNECTPOINT) && isConnectPoint(DEVICE_CONNECTPOINT, FieldPresence.MANDATORY);
    }

    /**
	 * Returns the device connect point
	 * 
	 * @return connect point or null if not set
	 */
	public ConnectPoint devicePoint() {
		String connectPoint = get(DEVICE_CONNECTPOINT, null);
		return connectPoint != null ? ConnectPoint.deviceConnectPoint(connectPoint) : null;
	}
}
