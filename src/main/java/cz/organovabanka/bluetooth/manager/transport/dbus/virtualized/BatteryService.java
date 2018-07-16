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

import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezService;
import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezContext;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezException;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezObjectBase;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.Service;

import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Virtual battery service, emulating "fake" battery service and characteristic for Bluez 5.48+
 * @author Lukas Rucka
 */
public class BatteryService extends AbstractBluezService {
    private static final Logger logger = LoggerFactory.getLogger(BatteryService.class);

    protected static final String BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb";
    public static final String BLUEZ_IFACE_BATTERY = "org.bluez.Battery1";

    public BatteryService(BluezContext context, String dbusObjectPath) {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_DEVICE);

        cache.set("url", BluezCommons.DBUSB_PROTOCOL_NAME + "://XX:XX:XX:XX:XX:XX/YY:YY:YY:YY:YY:YY/" + BATTERY_SERVICE_UUID);

        updateURL();
    }

    protected Logger getLogger() {
        return logger;
    }   

    protected void updateURL() throws BluezException {
        // this is the remote part of getURL
        try {
            AbstractBluezDevice device = context.getManagedDevice(dbusObjectPath, false);
            URL url = device.getURL().copyWithService(BATTERY_SERVICE_UUID);
            cache.set("url", url.toString());
        } catch (BluezException e) {
            getLogger().error("{}: Unable to update URL, reason: {}", dbusObjectPath, e.getMessage());
        }
    }   

    @Override
    public List<Characteristic> getCharacteristics() throws BluezException {
        Supplier<AbstractBluezCharacteristic> constructor = new Supplier<AbstractBluezCharacteristic>() {
            public AbstractBluezCharacteristic get() {
                return new BatteryLevelCharacteristic(context, dbusObjectPath);
            }
        };

        List<Characteristic> characteristics = new ArrayList();
    	Characteristic batteryCharacteristic = context.getManagedCharacteristic(dbusObjectPath + "/virt180f/char2a19", constructor);
        characteristics.add(batteryCharacteristic);
        return characteristics;
    }

    protected void disposeRemote() {
        ;
    }
}

