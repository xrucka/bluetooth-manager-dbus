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
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezService;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezService;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.Introspectable;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NativeBluezHooks {
    public static class NativePostAdapterDiscovery implements BluezHooks.PostAdapterDiscoveryHook {
        private static Logger logger = LoggerFactory.getLogger(NativePostAdapterDiscovery.class);
        public static BluezAdapter probeAdd(BluezContext context, String objpath) {
            if (!objpath.equals(BluezCommons.parsePath(objpath, BluezAdapter.class))) {
                logger.error("BUG: Adapter probe fired for a non-adapter path: {}", objpath);
                return null;
            }

            BluezAdapter _adapter = new NativeBluezAdapter(context, objpath);
            BluezAdapter adapter = context.emplaceAdapter(_adapter.getURL(), () -> _adapter);
            
            if (!(adapter instanceof NativeBluezAdapter)) {
                // should be always false, anyway
                logger.error("BUG: Some other adapter implementation in place of native bluez adapter: {}", adapter.getClass().getName());
            } else {
                NativeBluezAdapter proxiesAdapter = (NativeBluezAdapter)adapter;
                context.pushPathURL(BluezCommons.BLUEZ_IFACE_ADAPTER, proxiesAdapter.getPath(), proxiesAdapter.getURL());
            }

            return adapter;
        }

        public void trigger(BluezContext context, List<BluezAdapter> discoveredAdapters) {
            List<String> subpaths = BluezCommons.introspectSubpaths(context, "/org/bluez");

            // .map((node) -> "/org/bluez/" + node.getName())
            Set<BluezAdapter> allAdapters = subpaths.stream()
                .map((nodepath) -> probeAdd(context, nodepath))
                .collect(Collectors.toSet());
            discoveredAdapters.addAll(allAdapters);
        }
    }

    public static class NativePostDeviceDiscovery implements BluezHooks.PostDeviceDiscoveryHook {
        private static Logger logger = LoggerFactory.getLogger(NativePostDeviceDiscovery.class);
        public static BluezDevice probeAdd(BluezContext context, URL parentURL, String objpath) {
            if (!objpath.equals(BluezCommons.parsePath(objpath, BluezDevice.class))) {
                logger.error("BUG: Device probe fired for a non-device path: {}", objpath);
                return null;
            }

            BluezDevice _device = new NativeBluezDevice(context, objpath, parentURL);
            BluezDevice device = context.emplaceDevice(_device.getURL(), () -> _device);
            
            if (!(device instanceof NativeBluezDevice)) {
                // should be always false, anyway
                logger.error("BUG: Some other device implementation in place of native bluez device: {}", device.getClass().getName());
            } else {
                NativeBluezDevice proxiesDevice = (NativeBluezDevice)device;
                context.pushPathURL(BluezCommons.BLUEZ_IFACE_DEVICE, proxiesDevice.getPath(), proxiesDevice.getURL());
            }

            return device;
        }


        public void trigger(BluezContext context, BluezAdapter parentAdapter, List<BluezDevice> discoveredDevices) throws BluezException {
            // ignore virtual BluezAdapters
            if (!(parentAdapter instanceof NativeBluezAdapter)) {
                return;
            }

            final NativeBluezAdapter adapter = (NativeBluezAdapter)parentAdapter;
            List<String> subpaths = BluezCommons.introspectSubpaths(context, adapter.getPath());

            Set<BluezDevice> allDevices = subpaths.stream()
                .map((nodepath) -> probeAdd(context, adapter.getURL(), nodepath))
                .collect(Collectors.toSet());
            discoveredDevices.addAll(allDevices);
        }
    }

    public static class NativePostServiceDiscovery implements BluezHooks.PostServiceDiscoveryHook {
        private static Logger logger = LoggerFactory.getLogger(NativePostServiceDiscovery.class);
        public static BluezService probeAdd(BluezContext context, URL parentURL, String objpath) {
            if (!objpath.equals(BluezCommons.parsePath(objpath, BluezService.class))) {
                logger.error("BUG: Service probe fired for a non-service path: {}", objpath);
                return null;
            }

            //BluezService service = context.emplaceService(serviceURL, () -> new NativeBluezService(context, objpath, parentURL));
            // services are special and serve more like proxies -- they're not kept track of
            BluezService service = new NativeBluezService(context, objpath, parentURL);
            return service;
        }

        public void trigger(BluezContext context, BluezDevice parentDevice, List<BluezService> discoveredServices) {
            // ignore virtual BluezDevices
            if (!(parentDevice instanceof NativeBluezDevice)) {
                return;
            }

            final NativeBluezDevice device = (NativeBluezDevice)parentDevice;

            if (!parentDevice.isConnected()) {
                return;
            }

            List<String> subpaths = BluezCommons.introspectSubpaths(context, device.getPath());

            Set<BluezService> allServices = subpaths.stream()
                .map((nodepath) -> probeAdd(context, device.getURL(), nodepath))
                .collect(Collectors.toSet());
            discoveredServices.addAll(allServices);
        }
    }

    public static class NativePostCharacteristicDiscovery implements BluezHooks.PostCharacteristicDiscoveryHook {
        private static Logger logger = LoggerFactory.getLogger(NativePostCharacteristicDiscovery.class);
        public static BluezCharacteristic probeAdd(BluezContext context, URL parentURL, String objpath) {
            if (!objpath.equals(BluezCommons.parsePath(objpath, BluezCharacteristic.class))) {
                logger.error("BUG: Characteristic probe fired for a non-characteristic path: {}", objpath);
                return null;
            }

            final BluezCharacteristic _characteristic = new NativeBluezCharacteristic(context, objpath, parentURL);
            BluezCharacteristic characteristic = context.emplaceCharacteristic(_characteristic.getURL(), () -> _characteristic);

            if (!(characteristic instanceof NativeBluezCharacteristic)) {
                // should be always false, anyway
                logger.error("BUG: Some other characteristic implementation in place of native bluez characteristic: {}", characteristic.getClass().getName());
            } else {
                NativeBluezCharacteristic proxiesCharacteristic = (NativeBluezCharacteristic)characteristic;
                context.pushPathURL(BluezCommons.BLUEZ_IFACE_CHARACTERISTIC, proxiesCharacteristic.getPath(), proxiesCharacteristic.getURL());
            }

            return characteristic;
        }


        public void trigger(BluezContext context, BluezService parentService, List<BluezCharacteristic> discoveredCharacteristics) throws BluezException {
            // ignore virtual BluezServices
            if (!(parentService instanceof NativeBluezService)) {
                return;
            }

            final NativeBluezService service = (NativeBluezService)parentService;
            List<String> subpaths = BluezCommons.introspectSubpaths(context, service.getPath());

            Set<BluezCharacteristic> allCharacteristics = subpaths.stream()
                .map((nodepath) -> probeAdd(context, service.getURL(), nodepath))
                .collect(Collectors.toSet());
            discoveredCharacteristics.addAll(allCharacteristics);
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

