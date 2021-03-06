package cz.organovabanka.bluetooth.manager.transport.dbus;

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
import org.freedesktop.DBus.Properties;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusInterfaceName;
import org.freedesktop.dbus.DBusMemberName;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;

import java.lang.Byte;
import java.lang.IllegalArgumentException;
import java.lang.IllegalStateException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common paths and names for BlueZ.
 * @author Lukas Rucka
 */
public class BluezCommons {
    public static final String BLUEZ_IFACE_ADAPTER = "org.bluez.Adapter1";
    public static final String BLUEZ_IFACE_DEVICE = "org.bluez.Device1";
    public static final String BLUEZ_IFACE_SERVICE = "org.bluez.GattService1";
    public static final String BLUEZ_IFACE_CHARACTERISTIC = "org.bluez.GattCharacteristic1";
    public static final String BLUEZ_IFACE_DESCRIPTOR = "org.bluez.GattDescriptor1";
    public static final String BLUEZ_DBUS_BUSNAME = "org.bluez";
    public static final String BLUEZ_DBUS_OBJECT = "/org/bluez";
    public static final String DBUS_DBUS_BUSNAME = "org.freedesktop.DBus";
    public static final String DBUS_DBUS_OBJECT = "/org/freedesktop/DBus";
    public static final String DBUSB_PROTOCOL_NAME = "bluez";

    private static final Logger LOGGER = LoggerFactory.getLogger(BluezCommons.class);

    static final Pattern makeAdapterPathPattern() {
        return Pattern.compile("^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+$");
    }

    static final Pattern makeDevicePathPattern(String adapterPath) {
        return Pattern.compile("^" + adapterPath + "/dev(_[0-9a-fA-F]{2}){6}$");
    }

    static final Pattern makeServicePathPattern(String devicePath) {
        return Pattern.compile("^" + devicePath + "/service[0-9a-fA-F]{4}$");
    }

    static final Pattern makeCharacteristicPathPattern(String servicePath) {
        return Pattern.compile("^" + servicePath + "/char[0-9a-fA-F]{4}$");
    }

    static final String parsePath(String objectPath, Class t) {
        Class[] keys = { BluezAdapter.class, BluezDevice.class, BluezService.class, BluezCharacteristic.class, null };
        String[] patterns = {
            "^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+",
            "^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+/dev(_[0-9a-fA-F]{2}){6}",
            "^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+/dev(_[0-9a-fA-F]{2}){6}/service[0-9a-fA-F]{4}",
            "^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+/dev(_[0-9a-fA-F]{2}){6}/service[0-9a-fA-F]{4}/char[0-9a-fA-F]{4}",
            "^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+/dev(_[0-9a-fA-F]{2}){6}/service[0-9a-fA-F]{4}/char[0-9a-fA-F]{4}/desc[0-9a-fA-F]{4}$"
        };

        for (int i = 0; i < keys.length; ++i) {
            if (t != keys[i]) {
                continue;
            }
        
            Pattern pattern = Pattern.compile(patterns[i]);
            Matcher matcher = pattern.matcher(objectPath);
            return (matcher.find()) ? matcher.group() : null;
        }
        throw new IllegalArgumentException("Invalid class requested");
    }

    private static String matchBluezObject(
        Map<Path, Map<String, Map<String, Variant>>> allObjects, 
        Pattern pattern, 
        String iface, 
        String key, 
        String value
    ) {
        if (allObjects == null) {
            LOGGER.error("Bluez subsystem not available");
            return null;
        }

        for (Map.Entry<Path, Map<String, Map<String, Variant>>> entry : allObjects.entrySet()) {
            String path = entry.getKey().toString();
            if (!pattern.matcher(path).matches()) {
                continue;
            }

            Map<String, Map<String, Variant>> details = entry.getValue();
            String keyval = details.get(iface).get(key).getValue().toString();

            if (keyval.toLowerCase() == value.toLowerCase()) {
                return path;
            }
        }

        return null;
    }

    public static String pathForUrl(BluezContext context, URL url, Class stopper) throws BluezException {
        if (url.getProtocol() != BluezCommons.DBUSB_PROTOCOL_NAME) {
            throw new BluezException("Invalid protocol " + url.getProtocol() + " for this transport provider");
        }

        ObjectManager objectManager = null;
        try {
            objectManager = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
        } catch (DBusException e) {
            throw new BluezException("Unable to access dbus base services on bus " + BluezCommons.BLUEZ_DBUS_BUSNAME, e);
        }

        Map<Path, Map<String, Map<String, Variant>>> allObjects = objectManager.GetManagedObjects();
        if (allObjects == null) {
            LOGGER.error("Bluez subsystem not available");
            return null;
        }

        String adapterMac = url.getAdapterAddress();
        Pattern adapterPattern = BluezCommons.makeAdapterPathPattern();
        String adapterPath = matchBluezObject(allObjects, adapterPattern, BluezCommons.BLUEZ_IFACE_ADAPTER, "Address", adapterMac);

        if (stopper == BluezAdapter.class) {
            return adapterPath;
        }
        if (adapterPath == null) {
            LOGGER.debug("Inexistent adapter requested (" + adapterMac + ")");
            return null;
        }

        String deviceMac = url.getDeviceAddress();
        Pattern devicePattern = BluezCommons.makeDevicePathPattern(adapterPath);
        String devicePath = matchBluezObject(allObjects, devicePattern, BluezCommons.BLUEZ_IFACE_DEVICE, "Address", deviceMac);

        if (stopper == BluezDevice.class) {
            return devicePath;
        }
        if (devicePath == null) {
            LOGGER.debug("Device not found under such adapter (" + deviceMac + ")");
            return null;
        }

        String serviceUuid = url.getServiceUUID();
        Pattern servicePattern = BluezCommons.makeServicePathPattern(devicePath);
        String servicePath = matchBluezObject(allObjects, servicePattern, BluezCommons.BLUEZ_IFACE_SERVICE, "UUID", serviceUuid);

        if (stopper == BluezService.class) {
            return servicePath;
        }
        if (servicePath == null) {
            LOGGER.debug("Service not found under such device (service " + serviceUuid + ")");
            return null;
        }

        String characteristicUuid = url.getCharacteristicUUID();
        Pattern characteristicPattern = BluezCommons.makeServicePathPattern(devicePath);
        String characteristicPath = matchBluezObject(allObjects, characteristicPattern, BluezCommons.BLUEZ_IFACE_SERVICE, "UUID", characteristicUuid);

        if (stopper == BluezCharacteristic.class) {
            return characteristicPath;
        }

        LOGGER.trace("Characteristic not found under service (characteristic " + characteristicUuid + ")");
        return null;
    }

    public static interface PropertyCache {
        public void load(Map<String, Variant> values);
    }

    static <T> T readProperty(String iface, String property, Properties props, String objectPath) throws BluezException {
        try {
            return (T)props.Get(iface, property);
        } catch (RuntimeException e) {
            throw new BluezException("Unable to read property " + objectPath + ":" + property + " of: " + e.toString(), e);
        }
    }

    static <T> void writeProperty(String iface, String property, T value, Properties props, String objectPath) throws BluezException {
        try {
            props.Set(iface, property, value);
        } catch (RuntimeException e) {
            throw new BluezException("Unable to write property " + objectPath + ":" + property + " of: " + e.toString(), e);
        }
    }
}
