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

import org.freedesktop.dbus.Variant;

import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezContext;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezException;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.DataConversionUtils;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import java.util.List;
import java.util.Collections;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * Virtual battery characteristic, emulating "fake" battery service and characteristic for Bluez 5.48+
 * @author Lukas Rucka
 */
class BatteryLevelCharacteristic extends AbstractBluezCharacteristic {
    private static final Logger logger = LoggerFactory.getLogger(BatteryLevelCharacteristic.class);

    private static final String BATTERY_LEVEL_CHARACTERISTIC_UUID = "00002a19-0000-1000-8000-00805f9b34fb";
    public static final String BLUEZ_IFACE_BATTERY = BatteryService.BLUEZ_IFACE_BATTERY;
    private static final Variant flags = new Variant(Stream.of(new String[]{"read"}).collect(Collectors.toList()), "as");

    BatteryLevelCharacteristic(BluezContext context, String dbusObjectPath) {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_DEVICE);

        cache.set("UUID", BATTERY_LEVEL_CHARACTERISTIC_UUID);
        cache.set("Flags", flags);

        // percentage cache should be (in fact) synchronized with device, as it is device's attribute
        // however, for sake of simplicity...
        cache.set("Percentage", new Variant(new Byte((byte)0), "y"));

        updateURL();
    }

    protected Logger getLogger() {
        return logger;
    }   

    protected void updateURL() throws BluezException {
        // this is the remote part of getURL
        try {
            AbstractBluezDevice device = context.getManagedDevice(dbusObjectPath, false);
            URL url = device.getURL().copyWithService(BatteryService.BATTERY_SERVICE_UUID).copyWithCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID);
            cache.set("url", url.toString());
        } catch (BluezException e) {
            getLogger().error("{}: Unable to update URL, reason: {}", dbusObjectPath, e.getMessage());
        }
    }   
    
    @Override
    public boolean isNotifying() {
        return false;
    }   


    protected byte[] readValueRemote() {
        String property = "Percentage";
        byte[] value = new byte[1];
        Byte bval = cache.<Byte>get(property);
        try {
            bval = this.<Byte>readProperty(BLUEZ_IFACE_BATTERY, property);
            cache.update(property, new Variant(bval, "y"));
        } catch (Exception e) {
            getLogger().debug("{}:{} Error reading property, reason: {}", dbusObjectPath, property, e.getMessage());
        }
        value[0] = bval.byteValue();

        return value;
    } 

    @Override
    public byte[] readValue() throws BluezException, NotReadyException {
        getLogger().debug("{}: Reading battery level", dbusObjectPath);
        byte[] value = readValueRemote();
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("{}: Value read: {}", dbusObjectPath, DataConversionUtils.convert(value, 16));
        }

        return value;
    }

    @Override
    public boolean isNotificationConfigurable() {
        return false;
    }

    @Override
    public boolean writeValue(byte[] bytes) throws BluezException {
        getLogger().error("{}: BUG: cannot write value on read-only characteristic (virtual battery)", dbusObjectPath);
        return false;
    }
    
    protected void disposeRemote() {
        ;
    }
}

