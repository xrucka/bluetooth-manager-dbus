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
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusInterfaceName;
import org.freedesktop.dbus.DBusMemberName;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.transport.Adapter;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.Device;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Properties;

/**
 * A Bluetooth Manager Transport abstraction layer implementation based on java dbus binding.
 * @author Lukas Rucka
 */
public class BluezFactory implements BluetoothObjectFactory {
    private static final Logger logger = LoggerFactory.getLogger(BluezFactory.class);

    private static final ExecutorService NOTIFICATION_SERVICE = Executors.newCachedThreadPool();
    private static final ScheduledExecutorService repopulationService = Executors.newScheduledThreadPool(1);

    private final BluezContext context;

    private class Binder implements Runnable {
        public void run() {
            try {
                DBus dbus = context.getDbusConnection().getRemoteObject(BluezCommons.DBUS_DBUS_BUSNAME, BluezCommons.DBUS_DBUS_OBJECT, DBus.class);
                if (!dbus.NameHasOwner(BluezCommons.BLUEZ_DBUS_BUSNAME)) {
                    // have not found bluez daemon, reschedule
                    repopulationService.schedule(this, 15, SECONDS);
                }
            } catch (DBusException e) {
                logger.error("Cannot check bluetooth daemon: {}", e.getMessage());
                repopulationService.schedule(this, 15, SECONDS);
                return;
            }

            logger.info("Found running bluetooth daemon, connecting & populating...");

            context.bind();
            populate();
        };
    }
    private final Runnable binder = new Binder();

    private class Unbinder implements Runnable {
        public void run() {
            logger.error("Bluetooth daemon disappeared from system bus. Awaiting for new connection...");
            context.unbind();
        };
    }
    private final Runnable unbinder = new Unbinder();

    private class AddedHandler implements DBusSigHandler<ObjectManager.InterfacesAdded> {
        public void handle(ObjectManager.InterfacesAdded s) {
            String objpath = s.getObjectPath().toString();
            for (Map.Entry<String, Map<String, Variant>> pathEntry : s.getInterfacesAdded().entrySet()) {
                probeAdd(objpath, pathEntry.getKey(), pathEntry.getValue());
            }
        }
    }

    private class RemovedHandler implements DBusSigHandler<ObjectManager.InterfacesRemoved> {
        public void handle(ObjectManager.InterfacesRemoved s) {
            String objpath = s.getObjectPath().toString();
            
            if (BluezCommons.BLUEZ_DBUS_OBJECT.equals(objpath)) {
                // bluez object disappeared - that means, the bluez daemon was restarted
                repopulate();
                return;
            }

            for (String iface : s.getInterfacesRemoved()) {
                probeDrop(objpath, iface);
            }
        }
    }

    public BluezFactory() throws BluezException {
        context = new BluezContext();

        context.setupHandlers(new AddedHandler(), new RemovedHandler(), new PropertiesChangedHandler(context));
        repopulationService.schedule(binder, 0, SECONDS);
    }

    public synchronized void probeAdd(String objpath, String iface, Map<String, Variant> vals) {
        if (iface.equals(BluezCommons.BLUEZ_IFACE_ADAPTER)) {
            logger.debug("{}: discovered bluetooth adapter", objpath);
            BluezAdapter adapter = context.getManagedAdapter(objpath, true);
            adapter.getCache().update(vals);
            return;
        } else if (iface.equals(BluezCommons.BLUEZ_IFACE_DEVICE)) {
            logger.debug("{}: discovered bluetooth device", objpath);

            // ensure adapter exists before device gets added
            String adapterPath = BluezCommons.parsePath(objpath, BluezAdapter.class);
            BluezAdapter adapter = context.getManagedAdapter(adapterPath, true);

            BluezDevice device = context.getManagedDevice(objpath);
            device.getCache().update(vals);
            return;
        } else if (iface.equals(BluezCommons.BLUEZ_IFACE_CHARACTERISTIC)) {
            logger.debug("{}: discovered bluetooth service characteristic", objpath);

            // ensure adapter & device exist before characteristic gets added
            String devicePath = BluezCommons.parsePath(objpath, BluezDevice.class);
            BluezDevice device = context.getManagedDevice(devicePath);
            if (device == null) {
                // probe characteristic some time later
                return;
            }

            BluezCharacteristic characteristic = context.getManagedCharacteristic(objpath);
            characteristic.getCache().update(vals);
            return;
        }
    }

    public synchronized void probeDrop(String objpath, String iface) {
        if (iface.equals(BluezCommons.BLUEZ_IFACE_ADAPTER)) {
            logger.debug("{}: bluetooth adapter disappeared", objpath);
            context.disposeAdapter(objpath, false, false);
            return;
        } else if (iface.equals(BluezCommons.BLUEZ_IFACE_DEVICE)) {
            logger.debug("{}: bluetooth device disappeared", objpath);
            context.disposeDevice(objpath, false, false);
            return;
        } else if (iface.equals(BluezCommons.BLUEZ_IFACE_CHARACTERISTIC)) {
            logger.debug("{}: bluetooth service characteristic disappeared", objpath);
            context.disposeCharacteristic(objpath, false, false);
            return;
        }
    }

    void repopulate() {
        // ok, we will need futures for:

        // shuting down 
        repopulationService.schedule(unbinder, 1, SECONDS);

        // repopulation
        repopulationService.schedule(binder, 6, SECONDS);
    }

    private void populate() {
        Pattern adapterPattern = BluezCommons.makeAdapterPathPattern();
        Pattern devicePattern = BluezCommons.makeDevicePathPattern(".*/hci[0-9a-fA-F]+");
        DBusConnection systemBus = context.getDbusConnection();

        ObjectManager objectManager = null;

        /* populate adapters */
        try {
            synchronized (context.buslock) {
                objectManager = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
            }
        } catch (DBusException e) {
            throw new BluezException("Unable to access dbus objects to enumerate bluetooth adapters", e);
        }

        Map<Path, Map<String, Map<String, Variant>>> allObjects = null;
        try {
            synchronized (context.buslock) {
                allObjects = objectManager.GetManagedObjects();
            }
        } catch (RuntimeException ex) {
            throw new BluezException("Error populating adapters", ex);
        }

        if (allObjects == null) {
            throw new BluezException("Error populating adapters, got no objects");
        }

        // ensure adapters are populated before adding devices
        allObjects.entrySet().stream()
            .filter((entry) -> { return adapterPattern.matcher(entry.getKey().toString()).matches(); })
            .forEach((entry) -> {
                probeAdd(entry.getKey().toString(), BluezCommons.BLUEZ_IFACE_ADAPTER, entry.getValue().get(BluezCommons.BLUEZ_IFACE_ADAPTER));
            });

        allObjects.entrySet().stream()
            .filter((entry) -> { return devicePattern.matcher(entry.getKey().toString()).matches(); })
            .forEach((entry) -> {
                probeAdd(entry.getKey().toString(), BluezCommons.BLUEZ_IFACE_DEVICE, entry.getValue().get(BluezCommons.BLUEZ_IFACE_DEVICE));
            });
    }

    @Override
    public BluezAdapter getAdapter(URL url) throws BluezException {
        try {
            return context.getManagedAdapter(url);
        } catch (NullPointerException e) {
            logger.debug("Unable to get adapter by URL: {}, reason: {}", url, e);
        }
        return null;
    }

    @Override
    public BluezDevice getDevice(URL url) throws BluezException {
        try {
            return context.getManagedDevice(url);
        } catch (NullPointerException e) {
            logger.debug("Unable to get device by URL: {}, reason: {}", url, e);
        }
        return null;
    }

    @Override
    public Characteristic getCharacteristic(URL url) throws BluezException {
        try {
            // do not return characteristics for not-connected devices
            BluezCharacteristic target = context.getManagedCharacteristic(url);
            // get corresponding device
            BluezDevice device = context.getManagedDevice(BluezCommons.parsePath(target.getPath(), BluezDevice.class), false);
            return device.isConnected() ? target : null;
        } catch (NullPointerException e) {
            logger.debug("Unable to get characteristic by URL: {}, reason: {}", url, e.getMessage());
        }
        return null;
    }

    @Override
    public Set<DiscoveredAdapter> getDiscoveredAdapters() throws BluezException {
        Collection<BluezAdapter> adapters = null;
        synchronized (context) {
                adapters = context.getManagedAdapters();
        }

        if (adapters.isEmpty()) {
            // have no bluetooth adapters, perhaps bluez reset?
            repopulationService.schedule(binder, 15, SECONDS);
        }

        return adapters.stream()
            .map((adapter) -> convert(adapter))
            .collect(Collectors.toSet());
    }

    @Override
    public Set<DiscoveredDevice> getDiscoveredDevices() throws BluezException {
        Collection<BluezDevice> devices = null;
        Collection<BluezAdapter> adapters = Collections.emptySet();

        synchronized (context) {
            adapters = context.getManagedAdapters();
        }

        adapters.stream()
            .forEach((adapter) -> { adapter.getDevices(); });

        synchronized (context) {
            devices = context.getManagedDevices();
        }

        return devices.stream()
            .map((device) -> { device.activate(); return device; })
            .filter((device) -> { 
                return device.isActive();
            })
            .map((device) -> convert(device))
            .collect(Collectors.toSet());
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
        context.unbind();
        context.dispose();
    }

    @Override
    public void dispose(URL url) {
        if (url.isAdapter()) {
            BluezAdapter adapter = getAdapter(url);
            if (adapter == null) {
                logger.debug("Requested disposal of allready disposed adapter under {}", url.toString());
                return;
            } 

            //adapter.dispose(true, true);
            //context.disposeAdapter(adapter.getPath(), true, true);
            // dispose only removed objects, when dispose is called, deactivate device instead
            adapter.suspend(2);
    
        } else if (url.isDevice()) {
            BluezDevice device = getDevice(url);
            if (device == null) {
                logger.debug("Requested disposal of allready disposed device under {}", url.toString());
                return;
            }

            //device.dispose(true, true);
            //context.disposeDevice(device.getPath(), true, true);
            // dispose only removed objects, when dispose is called, deactivate device instead

            if (!device.isPaired() && !device.isTrusted()) {
                String adapterPath = device.getAdapterPath();
                BluezAdapter adapter = context.getManagedAdapter(adapterPath);
                adapter.removeDevice(device.getPath());
            } else {
                device.suspend(2);
            }

        }
    }

    static void runSilently(Runnable func) {
        try {
            func.run();
        } catch (Exception ignore) { /* do nothing */ }
    }

    static void notifySafely(Runnable noticator, Logger logger, String path) {
        getNotificationService().submit(() -> {
            try {
                noticator.run();
            } catch (RuntimeException e) {
                logger.error("Notification on " + path + " error: " + e.toString() + " " + e.getCause().toString());
            } catch (Exception e) {
                logger.error("Notification on " + path + " error: " + e.toString() + " " + e.getCause().toString());
            }
        });
    }

    // done
    private static ExecutorService getNotificationService() {
        return NOTIFICATION_SERVICE;
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
