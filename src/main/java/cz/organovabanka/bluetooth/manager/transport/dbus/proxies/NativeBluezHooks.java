package cz.organovabanka.bluetooth.manager.transport.dbus.proxies;

/*-
 * #%L
 * cz.organovabanka:bluetooth-manager-dbus
 * %%
 * Copyright (C) 2018 Lukas Rucka
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use adapter file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezContext;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezException;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezHooks;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Adapter1;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezService;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezService;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NativeBluezHooks {
    public static class NativePostAdapterDiscovery implements BluezHooks.PostAdapterDiscoveryHook {
        public static BluezAdapter probeAdd(BluezContext context, String objpath, Map<String, Variant> vals) {
            String address = (String)vals.get("Address").getValue();
            URL adapterURL = new URL(BluezCommons.DBUSB_PROTOCOL_NAME + "://" + address);
            BluezAdapter adapter = context.emplaceAdapter(adapterURL, () -> new NativeBluezAdapter(context, objpath));
            
            if (!(adapter instanceof NativeBluezAdapter)) {
                // should be always false, anyway
                Logger logger = LoggerFactory.getLogger(NativePostAdapterDiscovery.class);
                logger.error("BUG: Some other adapter implementation in place of native bluez adapter: {}", adapter.getClass().getName());
            } else {
                NativeBluezAdapter proxiesAdapter = (NativeBluezAdapter)adapter;
                context.pushPathURL(BluezCommons.BLUEZ_IFACE_ADAPTER, proxiesAdapter.getPath(), proxiesAdapter.getURL());
            }

            return adapter;
        }

        public void trigger(List<BluezAdapter> discoveredAdapters, BluezContext context) {
            Pattern adapterPattern = BluezCommons.makeAdapterPathPattern();
            Pattern devicePattern = BluezCommons.makeDevicePathPattern(".*/hci[0-9a-fA-F]+");

            ObjectManager objectManager = null;

            /* populate adapters */
            try {
                objectManager = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
            } catch (DBusException e) {
                throw new BluezException("Unable to access dbus objects to enumerate bluetooth adapters", e); 
            }

            Map<Path, Map<String, Map<String, Variant>>> allObjects = null;
            try {
                allObjects = objectManager.GetManagedObjects();
            } catch (RuntimeException ex) {
                throw new BluezException("Error populating adapters", ex);
            }

            if (allObjects == null) {
                throw new BluezException("Error populating adapters, got no objects");
            }

            // populate only adapters, the remains will be added iteratively
            // control loop is given by bluetooth manager
            // ensure adapters are populated before adding devices
            Collection<BluezAdapter> allAdapters = allObjects.entrySet().stream()
                .filter((entry) -> adapterPattern.matcher(entry.getKey().toString()).matches())
                .map((entry) -> probeAdd(context, entry.getKey().toString(), entry.getValue().get(BluezCommons.BLUEZ_IFACE_ADAPTER)))
                .collect(Collectors.toSet());

            discoveredAdapters.addAll(allAdapters);
        }
    }

    public static class NativePostDeviceDiscovery implements BluezHooks.PostDeviceDiscoveryHook {
        public static BluezDevice probeAdd(BluezContext context, URL parentURL, String objpath, Map<String, Variant> vals) {
            String address = (String)vals.get("Address").getValue();
            URL deviceURL = parentURL.copyWithDevice(address);

            BluezDevice device = context.emplaceDevice(deviceURL, () -> new NativeBluezDevice(context, objpath, parentURL));
            
            if (!(device instanceof NativeBluezDevice)) {
                // should be always false, anyway
                Logger logger = LoggerFactory.getLogger(NativePostDeviceDiscovery.class);
                logger.error("BUG: Some other device implementation in place of native bluez device: {}", device.getClass().getName());
            } else {
                NativeBluezDevice proxiesDevice = (NativeBluezDevice)device;
                context.pushPathURL(BluezCommons.BLUEZ_IFACE_DEVICE, proxiesDevice.getPath(), proxiesDevice.getURL());
            }

            return device;
        }


        public void trigger(BluezAdapter parentAdapter, List<BluezDevice> discoveredDevices, BluezContext context) throws BluezException {
            // ignore virtual BluezAdapters
            if (!(parentAdapter instanceof NativeBluezAdapter)) {
                return;
            }

            final NativeBluezAdapter adapter = (NativeBluezAdapter)parentAdapter;
            final String adapterPath = adapter.getPath();
            final Pattern devicePattern = Pattern.compile("^" + adapterPath + "/dev(_[0-9a-fA-F]{2}){6}$");

            Map<Path, Map<String, Map<String, Variant>>> allObjects = null;
            try {
                ObjectManager objectManager = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
                allObjects = objectManager.GetManagedObjects();
            } catch (DBusException e) {
                throw new BluezException("Unable to enumerate bluetooth objects when processing " + adapter.getPath(), e);
            } catch (RuntimeException e) {
                throw new BluezException("Unable to enumerate bluetooth objects when processing " + adapter.getPath(), e);
            }

            try {
                Collection<BluezDevice> allDevices = allObjects.entrySet().stream()
                    .filter((entry) -> devicePattern.matcher(entry.getKey().toString()).matches())
                    .map((entry) -> probeAdd(context, adapter.getURL(), entry.getKey().toString(), entry.getValue().get(BluezCommons.BLUEZ_IFACE_DEVICE)))
                    .filter((device) -> (device.getRSSI() != 0))
                    .collect(Collectors.toSet());

                discoveredDevices.addAll(allDevices);
            } catch (RuntimeException e) {
                throw new BluezException("Unable to unpack bluez objects when processing " + adapter.getPath(), e);
            }
        }
    }

    public static class NativePostServiceDiscovery implements BluezHooks.PostServiceDiscoveryHook {
        public static BluezService probeAdd(BluezContext context, URL parentURL, String objpath, Map<String, Variant> vals) {
            String uuid = (String)vals.get("UUID").getValue();
            URL serviceURL = parentURL.copyWithService(uuid);

            //BluezService service = context.emplaceService(serviceURL, () -> new NativeBluezService(context, objpath, parentURL));
            // services are special and serve more like proxies -- they're not kept track of
            BluezService service = new NativeBluezService(context, objpath, parentURL);
            return service;
        }

        public void trigger(BluezDevice parentDevice, List<BluezService> discoveredServices, BluezContext context) throws BluezException {
            // ignore virtual BluezDevices
            if (!(parentDevice instanceof NativeBluezDevice)) {
                return;
            }

            final NativeBluezDevice device = (NativeBluezDevice)parentDevice;

            if (!parentDevice.isConnected()) {
                return;
            }

            final String devicePath = device.getPath();
            final Pattern servicePattern = Pattern.compile("^" + devicePath + "/service[0-9a-fA-F]{4}$");

            Map<Path, Map<String, Map<String, Variant>>> allObjects = null;
            try {
                ObjectManager objectManager = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
                allObjects = objectManager.GetManagedObjects();
            } catch (DBusException e) {
                throw new BluezException("Unable to enumerate bluetooth objects when processing " + device.getPath(), e);
            } catch (RuntimeException e) {
                throw new BluezException("Unable to enumerate bluetooth objects when processing " + device.getPath(), e);
            }

            try {
                Collection<BluezService> allServices = allObjects.entrySet().stream()
                    .filter((entry) -> servicePattern.matcher(entry.getKey().toString()).matches())
                    .map((entry) -> probeAdd(context, device.getURL(), entry.getKey().toString(), entry.getValue().get(BluezCommons.BLUEZ_IFACE_SERVICE)))
                    .collect(Collectors.toSet());

                discoveredServices.addAll(allServices);
            } catch (RuntimeException e) {
                throw new BluezException("Unable to unpack bluez objects when processing " + device.getPath(), e);
            }
        }
    }

    public static class NativePostCharacteristicDiscovery implements BluezHooks.PostCharacteristicDiscoveryHook {
        public static BluezCharacteristic probeAdd(BluezContext context, URL parentURL, String objpath, Map<String, Variant> vals) {
            String address = (String)vals.get("Address").getValue();
            URL characteristicURL = parentURL.copyWithCharacteristic(address);

            BluezCharacteristic characteristic = context.emplaceCharacteristic(characteristicURL, () -> new NativeBluezCharacteristic(context, objpath, parentURL));
            
            if (!(characteristic instanceof NativeBluezCharacteristic)) {
                // should be always false, anyway
                Logger logger = LoggerFactory.getLogger(NativePostCharacteristicDiscovery.class);
                logger.error("BUG: Some other characteristic implementation in place of native bluez characteristic: {}", characteristic.getClass().getName());
            } else {
                NativeBluezCharacteristic proxiesCharacteristic = (NativeBluezCharacteristic)characteristic;
                context.pushPathURL(BluezCommons.BLUEZ_IFACE_CHARACTERISTIC, proxiesCharacteristic.getPath(), proxiesCharacteristic.getURL());
            }

            return characteristic;
        }


        public void trigger(BluezService parentService, List<BluezCharacteristic> discoveredCharacteristics, BluezContext context) throws BluezException {
            // ignore virtual BluezServices
            if (!(parentService instanceof NativeBluezService)) {
                return;
            }

            final NativeBluezService service = (NativeBluezService)parentService;
            final String servicePath = service.getPath();
            final Pattern characteristicPattern = Pattern.compile("^" + service.getPath() + "/char[0-9a-fA-F]{4}$");

            Map<Path, Map<String, Map<String, Variant>>> allObjects = null;
            try {
                ObjectManager objectManager = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
                allObjects = objectManager.GetManagedObjects();
            } catch (DBusException e) {
                throw new BluezException("Unable to enumerate bluetooth objects when processing " + service.getPath(), e);
            } catch (RuntimeException e) {
                throw new BluezException("Unable to enumerate bluetooth objects when processing " + service.getPath(), e);
            }

            try {
                Collection<BluezCharacteristic> allCharacteristics = allObjects.entrySet().stream()
                    .filter((entry) -> characteristicPattern.matcher(entry.getKey().toString()).matches())
                    .map((entry) -> probeAdd(context, service.getURL(), entry.getKey().toString(), entry.getValue().get(BluezCommons.BLUEZ_IFACE_CHARACTERISTIC)))
                    .collect(Collectors.toSet());

                discoveredCharacteristics.addAll(allCharacteristics);
            } catch (RuntimeException e) {
                throw new BluezException("Unable to unpack bluez objects when processing " + service.getPath(), e);
            }
        }
    }

    public static void register(BluezContext context) {
        BluezHooks hooks = context.getHooks();
        hooks.addPostAdapterDiscoveryHook(new NativePostAdapterDiscovery());
        hooks.addPostDeviceDiscoveryHook(new NativePostDeviceDiscovery());
        hooks.addPostServiceDiscoveryHook(new NativePostServiceDiscovery());
        hooks.addPostCharacteristicDiscoveryHook(new NativePostCharacteristicDiscovery());
    }

}

