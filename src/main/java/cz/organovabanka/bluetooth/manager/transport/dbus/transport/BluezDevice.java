package cz.organovabanka.bluetooth.manager.transport.dbus.transport;

/*-
 * #%L
 * cz.organovabanka:bluetooth-manager-dbus
 * %%
 * Copyright (C) 2018 Lukas Rucka
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.sputnikdev.bluetooth.manager.transport.Device;

/**
 * A interface common for all device implementations within this transport implementation
 * @author Lukas Rucka
 */
public interface BluezDevice extends Device {

    /*
     * Mark this instance used by bluetooth-manager
     */
    abstract void activate();

    /*
     * Mark this instance no longer used by bluetooth-manager
     */
    abstract void dispose();

    /*
     * Returns whether this instance is currently used by bluetooth manager
     */
    abstract boolean isActive();

    /*
     * Return MAC address of this device
     * Must match address obtained from URL
     */
    abstract String getAddress();

    /*
     * Check whether this bluetooth device is trusted to connect to this computer
     * Not currently used by bluetooth manager
     */
    abstract boolean isTrusted();

    /*
     * Check whether this bluetooth device has been paired with this computer
     * Not currently used by bluetooth manager
     */
    abstract boolean isPaired();
}
