package cz.organovabanka.bluetooth.manager.transport.dbus;

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

import static java.util.concurrent.TimeUnit.SECONDS;

import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezContext;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezException;
import cz.organovabanka.bluetooth.manager.transport.dbus.PropertiesChangedHandler;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezHooks;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezService;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.virtual.VirtualBatteryServiceHook;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.handlers.AbstractInterfacesAddedHandler;
import org.freedesktop.dbus.handlers.AbstractInterfacesRemovedHandler;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.NotConnected;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.transport.Adapter;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;
import org.sputnikdev.bluetooth.manager.transport.Device;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A Bluetooth Manager Transport abstraction layer implementation based on java dbus binding.
 * @author Lukas Rucka
 */
public class BluezFactory implements BluetoothObjectFactory {
    private static final Logger logger = LoggerFactory.getLogger(BluezFactory.class);

    private final ScheduledExecutorService repopulationService = Executors.newScheduledThreadPool(1);
    private final BluezContext context;

    private class Binder implements Runnable {
        public void run() {
            try {
                DBus dbus = context.getDbusConnection().getRemoteObject(BluezCommons.DBUS_DBUS_BUSNAME, BluezCommons.DBUS_DBUS_OBJECT, DBus.class);
                if (!dbus.NameHasOwner(BluezCommons.BLUEZ_DBUS_BUSNAME)) {
                    // have not found bluez daemon, reschedule
                    schedule(this, 15, SECONDS);
                }
/*
            } catch (NotConnected e) {
                logger.error("Disconnected dbus detected!");
                context.getDbusConnection().disconnect();
                context.connect();
                context.reset();
                context.setupHandlers(new AddedHandler(), new RemovedHandler(), new PropertiesChangedHandler(context));
*/
            } catch (DBusException e) {
                logger.error("Cannot check bluetooth daemon: {}", e.getMessage());
                schedule(this, 15, SECONDS);
                return;
            }

            logger.info("Found running bluetooth daemon, connecting & populating...");

            context.bind();
            populate();
        }
    }

    private final Runnable binder = new Binder();

    private class Unbinder implements Runnable {
        public void run() {
            logger.error("Bluetooth daemon disappeared from system bus. Awaiting for new connection...");
            context.unbind();
        }
    }

    private final Runnable unbinder = new Unbinder();

    private void schedule(Runnable that, long time, TimeUnit tu) {
        repopulationService.schedule(that, time, tu);
    }


    private URL makeAdapterURL(String adapterPath) {
        adapterPath = BluezCommons.parsePath(adapterPath, BluezAdapter.class);
        URL adapterURL = context.pathURL(BluezCommons.BLUEZ_IFACE_ADAPTER, adapterPath);

        if (adapterURL == null) {
            NativeBluezAdapter tmpAdapter = new NativeBluezAdapter(context, adapterPath);
            adapterURL = tmpAdapter.getURL();
        }

        return adapterURL;
    }

    private URL makeDeviceURL(String devicePath) {
        devicePath = BluezCommons.parsePath(devicePath, BluezDevice.class);
        URL deviceURL = context.pathURL(BluezCommons.BLUEZ_IFACE_DEVICE, devicePath);

        if (deviceURL == null) {
            NativeBluezDevice tmpDevice = new NativeBluezDevice(context, devicePath, makeAdapterURL(devicePath));
            deviceURL = tmpDevice.getURL();
        }

        return deviceURL;
    }


    public void probeAdd(String path, String dbusInterface, Map<String, Variant<?>> values) {
        // dbus-java 3.0.0 fires interfaces added even on root paths, not only correct paths; ignore the missfires by checking path
        if (dbusInterface.equals(BluezCommons.BLUEZ_IFACE_ADAPTER) && path.equals(BluezCommons.parsePath(path, BluezAdapter.class))) {
            (new NativeBluezHooks.NativePostAdapterDiscovery()).probeAdd(context, path, values);
        } else if (dbusInterface.equals(BluezCommons.BLUEZ_IFACE_DEVICE) && path.equals(BluezCommons.parsePath(path, BluezDevice.class))) {
            (new NativeBluezHooks.NativePostDeviceDiscovery()).probeAdd(context, makeAdapterURL(path), path, values);
        } else if (dbusInterface.equals(BluezCommons.BLUEZ_IFACE_CHARACTERISTIC) && path.equals(BluezCommons.parsePath(path, BluezCharacteristic.class))) {
            URL deviceURL = makeDeviceURL(path);
            URL serviceURL = new NativeBluezService(context, path, deviceURL).getURL();
            (new NativeBluezHooks.NativePostCharacteristicDiscovery()).probeAdd(context, serviceURL, path, values);
        }
        // no other handled
    }

    private class AddedHandler extends AbstractInterfacesAddedHandler {
        public void handle(ObjectManager.InterfacesAdded s) {
            String objpath = s.getObjectPath().toString();
            for (Map.Entry<String, Map<String, Variant<?>>> pathEntry : s.getInterfaces().entrySet()) {
                probeAdd(objpath, pathEntry.getKey(), pathEntry.getValue());
            }
        }
    }

    private class RemovedHandler extends AbstractInterfacesRemovedHandler {
        public void handle(ObjectManager.InterfacesRemoved s) {
            String objpath = s.getObjectPath().toString();
            if (BluezCommons.BLUEZ_DBUS_OBJECT.equals(objpath)) {
                // bluez object disappeared - that means, the bluez daemon was restarted
                repopulate();
                return;
            }

            synchronized (context) {
                for (String iface : s.getInterfaces()) {
                    URL targetURL = context.pathURL(iface, objpath);
                    if (targetURL == null) {
                        continue;
                    }

                    if (targetURL.isAdapter()) {
                        context.dropAdapter(targetURL);
                    } else if (targetURL.isDevice()) {
                        context.dropDevice(targetURL);
                    } else if (targetURL.isCharacteristic()) {
                        context.dropCharacteristic(targetURL);
                    }
                }
            }
        }
    }

    public BluezFactory() throws BluezException {
        context = new BluezContext();
        NativeBluezHooks.register(context);
        VirtualBatteryServiceHook.register(context);
        context.setupHandlers(new AddedHandler(), new RemovedHandler(), new PropertiesChangedHandler(context));
        schedule(binder, 0, SECONDS);
    }

    void repopulate() {
        // ok, we will need futures for:

        // shuting down 
        schedule(unbinder, 1, SECONDS);

        // repopulation
        schedule(binder, 6, SECONDS);
    }

    private void populate() {
        ObjectManager objectManager = null;

        /* populate adapters */
        try {
            objectManager = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
        } catch (DBusException e) {
            throw new BluezException("Unable to access dbus objects to enumerate bluetooth adapters", e);
        }

        Map<DBusPath, Map<String, Map<String, Variant<?>>>> allObjects = null;
        try {
            allObjects = objectManager.GetManagedObjects();
        } catch (RuntimeException ex) {
            throw new BluezException("Error populating adapters", ex);
        }

        if (allObjects == null) {
            throw new BluezException("Error populating adapters, got no objects");
        }

        // ensure adapters are populated before adding devices
        Pattern adapterPattern = BluezCommons.makeAdapterPathPattern();
        allObjects.entrySet().stream()
            .filter((entry) -> adapterPattern.matcher(entry.getKey().toString()).matches())
            .forEach((entry) -> probeAdd(entry.getKey().toString(), BluezCommons.BLUEZ_IFACE_ADAPTER, entry.getValue().get(BluezCommons.BLUEZ_IFACE_ADAPTER)));

        Pattern devicePattern = BluezCommons.makeDevicePathPattern(".*/hci[0-9a-fA-F]+");
        allObjects.entrySet().stream()
            .filter((entry) -> devicePattern.matcher(entry.getKey().toString()).matches())
            //.forEach((entry) -> logger.error("Matched device {} {}", entry.getKey().toString(), devicePattern.matcher(entry.getKey().toString()).matches()));
            .forEach((entry) -> probeAdd(entry.getKey().toString(), BluezCommons.BLUEZ_IFACE_DEVICE, entry.getValue().get(BluezCommons.BLUEZ_IFACE_DEVICE)));
    }

    @Override
    public BluezAdapter getAdapter(URL url) {
        BluezAdapter adapter = context.getManagedAdapter(url);
        if (adapter == null) {
            return null;
        }

        adapter.activate();
 
        return adapter;
    }

    @Override
    public BluezDevice getDevice(URL url) {
        BluezDevice device = context.getManagedDevice(url);
        if (device == null) {
            return null;
        }
        
        device.activate();

        return device;
    }

    @Override
    public BluezCharacteristic getCharacteristic(URL url) {
        BluezCharacteristic characteristic = context.getManagedCharacteristic(url);
        if (characteristic == null) {
            return null;
        }

        characteristic.activate();

        return characteristic;
    }

    @Override
    public Set<DiscoveredAdapter> getDiscoveredAdapters() {
        Collection<BluezAdapter> _adapters = context.getManagedAdapters();

        if (logger.isDebugEnabled()) {
            String report = _adapters.stream()
                .map((adapter) -> adapter.getURL().getAdapterAddress())
                .collect(Collectors.joining(", "));
            logger.debug("Discovered adapters: " + report);
        }

        Set<DiscoveredAdapter> adapters = _adapters.stream()
            .map((adapter) -> convert(adapter))
            .collect(Collectors.toSet());

        if (adapters.isEmpty()) {
            // have no bluetooth adapters, perhaps bluez reset?
            schedule(binder, 15, SECONDS);
        } else {
            // todo tripple the interval
            schedule(binder, 15, SECONDS);
        }

        return adapters;
    }

    @Override
    public Set<DiscoveredDevice> getDiscoveredDevices() {
        Collection<BluezDevice> _devices = context.getManagedDevices();

        if (logger.isDebugEnabled()) {
            String report = _devices.stream()
                .map((device) -> device.getURL().getDeviceAddress())
                .collect(Collectors.joining(", "));
            logger.debug("Discovered devices: " + report);
        }

        Set<DiscoveredDevice> devices = _devices.stream()
            .map((device) -> convert(device))
            .collect(Collectors.toSet());

        return devices;
    }

    // done
    @Override
    public String getProtocolName() {
        return BluezCommons.DBUSB_PROTOCOL_NAME;
    }

    // done
    @Override
    public void configure(Map<String, Object> config) { /* do nothing for now */ }

    /**
     * Disposing Bluez factory by closing/disposing all adapters, devices and services.
     */
    public void dispose() {
        logger.debug("BluezFactory: general dispose");

        repopulationService.shutdown();
        try {
            repopulationService.awaitTermination(30, SECONDS);
        } catch (InterruptedException e) {
            ; // silently ignore
        }

        context.unbind();
        context.dispose();
    }

    @Override
    public void dispose(URL url) {
        logger.debug("Dispose of {}", url.toString());
        if (url.isAdapter()) {
            context.disposeAdapter(url);
        } else if (url.isDevice()) {
            context.disposeDevice(url);
        } else if (url.isCharacteristic()) {
            context.disposeCharacteristic(url);
        }
    }

    public static void runSilently(Runnable func) {
        BluezCommons.runSilently(func);
    }

    // done
    private static void closeSilently(AutoCloseable autoCloseable) {
        try {
            autoCloseable.close();
        } catch (Exception ignore) { /* do nothing */ }
    }

    private static DiscoveredDevice convert(BluezDevice device) {
        return new DiscoveredDevice(
            device.getURL(),
            device.getName(), device.getAlias(),
            device.getRSSI(),
            device.getBluetoothClass(), device.isBleEnabled()
        );
    }

    private static DiscoveredAdapter convert(BluezAdapter adapter) {
        return new DiscoveredAdapter(
            adapter.getURL(),
            adapter.getName(), adapter.getAlias()
        );
    }
}
