package cz.organovabanka.bluetooth.manager.transport.dbus.virtual;

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

import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezContext;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezException;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezHooks;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezObject;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezService;

import org.freedesktop.DBus;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Introspectable;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Virtual battery service + characteristic, emulating "fake" battery service and characteristic for Bluez 5.48+
 * @author Lukas Rucka
 */
public class VirtualBatteryServiceHook implements BluezHooks.PostServiceDiscoveryHook {
    private static final String BLUEZ_IFACE_BATTERY = "org.bluez.Battery1";
    private final Logger logger = LoggerFactory.getLogger(VirtualBatteryServiceHook.class);

    /**
     * Interface mirroring Bluez battery interface methods.
     */
    @DBusInterfaceName(BLUEZ_IFACE_BATTERY)
    public interface Battery1 extends DBusInterface {
        //
    }

    private static final String BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb";
    private static final String BATTERY_LEVEL_CHARACTERISTIC_UUID = "00002a19-0000-1000-8000-00805f9b34fb";

    /**
     * Virtual battery characteristic, emulating "fake" battery service and characteristic for Bluez 5.48+
     */
    static class VirtualBatteryLevelCharacteristic extends NativeBluezObject implements BluezCharacteristic {
        private static final Logger logger = LoggerFactory.getLogger(VirtualBatteryLevelCharacteristic.class);

        private final URL objectURL;
        private Notification<byte[]> notificationData = null;

        VirtualBatteryLevelCharacteristic(BluezContext context, String dbusObjectPath, URL parentServiceURL) throws BluezException {
            super(context, dbusObjectPath, BLUEZ_IFACE_BATTERY);
            objectURL = makeURL(parentServiceURL);
        }

        @Override
        public URL getURL() {
            return objectURL;
        }

        protected URL makeURL(URL parentServiceURL) {
            return parentServiceURL.copyWithCharacteristic(getUUID());
        }

        public Logger getLogger() {
            return logger;
        }

        @Override
        public String getUUID() {
            return BATTERY_LEVEL_CHARACTERISTIC_UUID;
        }

        @Override
        public Set<CharacteristicAccessType> getFlags() {
            Set<CharacteristicAccessType> flags = new HashSet<>();
            flags.add(CharacteristicAccessType.READ);
            return flags;
        }

        @Override
        public boolean isNotifying() {
            return false;
        }

        @Override
        public byte[] readValue() {
            byte[] value = new byte[1];
            getLogger().error("VirtualBattery read invoked: {} ({})", getPath(), getURL());
            Byte bval = this.<Byte>readProperty("Percentage");
            getLogger().error("VirtualBattery: {} ({}) has {}", getPath(), getURL(), bval.toString());
            value[0] = bval.byteValue();
            return value;
        }

        @Override
        public void enableValueNotifications(Notification<byte[]> notification) {
            notificationData = notification;
        }

        @Override
        public void disableValueNotifications() {
            notificationData = null;
        }

        @Override
        public boolean writeValue(byte[] bytes) {
            //getLogger().error("{}: BUG: cannot write value on read-only characteristic (virtual battery)", dbusObjectPath);
            return false;
        }

        @Override
        public boolean isNotificationConfigurable() {
            return false;
        }
    }

    /*
     * Virtual battery service + characteristic, emulating "fake" battery service and characteristic for Bluez 5.48+
     */
    static class VirtualBatteryService extends NativeBluezObject implements BluezService {
        private static final Logger logger = LoggerFactory.getLogger(VirtualBatteryService.class);
        private final URL objectURL;
        private boolean active;

        public VirtualBatteryService(BluezContext context, String dbusObjectPath, URL parentDeviceURL) {
            super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_DEVICE);
            objectURL = makeURL(parentDeviceURL);
        }

        protected URL makeURL(URL parentDeviceURL) {
            return parentDeviceURL.copyWithService(getUUID());
        }   

        @Override
        public URL getURL() {
            return objectURL;
        }   

        public Logger getLogger() {
            return logger;
        }

        public String getUUID() {
            return BATTERY_SERVICE_UUID;
        }

        public boolean isActive() {
            return active;
        }

        public void activate() {
            active = true;
        }

        public void dispose() {
            active = false;
        }

        @Override
        public List<Characteristic> getCharacteristics() {
            List<BluezCharacteristic> characteristics = new ArrayList<>();

            BluezCharacteristic batteryCharacteristic = context.emplaceCharacteristic(
                getURL().copyWithCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID),
                () -> new VirtualBatteryLevelCharacteristic(context, dbusObjectPath, getURL())
            );
            if (!(batteryCharacteristic instanceof VirtualBatteryLevelCharacteristic)) {
                // should be always false, anyway
                logger.error("BUG: Some other characteristic implementation in place of virtual battery characteristic");
            }
            characteristics.add(batteryCharacteristic);
            return characteristics.stream().collect(Collectors.toList());
        }
    }

    private boolean hasBatteryService(BluezContext context, NativeBluezDevice device) {
        String probed = null;
        try {
            Introspectable probe = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, device.getPath(), Introspectable.class);
            probed = probe.Introspect();
        } catch (DBusExecutionException | DBusException ex) {
            logger.error("Error looking for battery service on {} ({}): {}, {}", device.getPath(), device.getURL(), ex.getClass().getName(), ex.getMessage());
            return false;
        }

        // check whether interface exists
        // extremely primitive :-/
        return probed.contains(BLUEZ_IFACE_BATTERY);
    }

    @Override
    public void trigger(BluezDevice device, List<BluezService> discoveredServices, BluezContext context) {
        if (!(device instanceof NativeBluezDevice)) {
            return;
        }

        NativeBluezDevice bdevice = (NativeBluezDevice)device;
        if (!hasBatteryService(context, bdevice)) {
            return;
        }

        discoveredServices.add(new VirtualBatteryService(context, bdevice.getPath(), bdevice.getURL()));
    }

    public VirtualBatteryServiceHook() {
        ;
    }

    public static void register(BluezContext context) {
        context.getHooks().addPostServiceDiscoveryHook(new VirtualBatteryServiceHook());
    }

}

