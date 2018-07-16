package cz.organovabanka.bluetooth.manager.transport.dbus.virtualized;

/*-
 * #%L
 * org.sputnikdev:bluetooth-manager-dbus
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

import org.freedesktop.DBus;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.manager.transport.Service;

import java.util.List;

import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezContext;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezException;
import cz.organovabanka.bluetooth.manager.transport.dbus.virtualized.Battery1;
import cz.organovabanka.bluetooth.manager.transport.dbus.virtualized.BatteryService;
import cz.organovabanka.bluetooth.manager.transport.dbus.virtualized.VirtualServiceInjector;

/**
 * A class representing Bluez devices.
 * @author Lukas Rucka
 */
public class BatteryServiceInjector implements VirtualServiceInjector {
    private static final Logger logger = LoggerFactory.getLogger(BatteryServiceInjector.class);
    private BluezContext context;

    public BatteryServiceInjector(BluezContext context) {
        this.context = context;
    } 

    public void inject(AbstractBluezDevice device, List<Service> alreadyDiscovered) {
        logger.trace("{}: Listing resolved virtual GATT services", device.getPath());

        if (!device.isConnected()) {
            return;
        }
        
        String probed = null;
        try {
            DBus.Introspectable probe = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, device.getPath(), DBus.Introspectable.class);
            probed = probe.Introspect();
        } catch (DBusExecutionException|DBusException ex) {
            logger.debug("{}: introspection probe failed: {}", device.getPath(), ex.getMessage());
            return;
        }

        // check whether interface exists
        // extremely primitive :-/
        if (!probed.contains(BatteryService.BLUEZ_IFACE_BATTERY)) {
            return;
        }

        alreadyDiscovered.add(new BatteryService(context, device.getPath()));
    }
}
