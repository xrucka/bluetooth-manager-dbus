package cz.organovabanka.bluetooth.manager.transport.dbus;

/*-
 * #%L
 * 
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
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sputnikdev.bluetooth.URL;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Properties;

/**
 * Context class for all Bluez related stuff
 * @author Lukas Rucka
 */
public class BluezContext {
    public final String buslock = "I don't really trust internal dbus-java locking, as I observed issues during development";
    private static final Logger logger = LoggerFactory.getLogger(BluezContext.class);

    private final DBusConnection busConnection;
    private String bluezProcessOwner;

    private DBusSigHandler<ObjectManager.InterfacesAdded> interfacesAddedHandler = null;
    private DBusSigHandler<ObjectManager.InterfacesRemoved> interfacesRemovedHandler = null;
    private DBusSigHandler<Properties.PropertiesChanged> propertiesChangedHandler = null;

    // keep handlers for distinct object paths
    private Map<String, BluezAdapter> adapters = new ConcurrentHashMap();
    private Map<String, BluezDevice> devices = new ConcurrentHashMap();
    private Map<String, BluezCharacteristic> characteristics = new ConcurrentHashMap();

    public BluezContext() throws BluezException {
        try {
            busConnection = DBusConnection.getConnection(DBusConnection.SYSTEM);
        } catch (DBusException e) {
            throw new BluezException("Unable to access dbus", e);
        }
    }

    public void setupHandlers(
        DBusSigHandler<ObjectManager.InterfacesAdded> interfacesAddedHandler,
        DBusSigHandler<ObjectManager.InterfacesRemoved> interfacesRemovedHandler,
        DBusSigHandler<Properties.PropertiesChanged> propertiesChangedHandler
    ) {
        this.interfacesAddedHandler = interfacesAddedHandler;
        this.interfacesRemovedHandler = interfacesRemovedHandler;
        this.propertiesChangedHandler = propertiesChangedHandler;
    }

    void bind() throws BluezException {
        if (bluezProcessOwner != null) {
            return;
        }

        try {
            synchronized (buslock) {
                DBus dbus = busConnection.getRemoteObject(BluezCommons.DBUS_DBUS_BUSNAME, BluezCommons.DBUS_DBUS_OBJECT, DBus.class);
                String tmpBluezProcessOwner = dbus.GetNameOwner(BluezCommons.BLUEZ_DBUS_BUSNAME);
                
                if (interfacesAddedHandler != null) {
                    busConnection.addSigHandler(ObjectManager.InterfacesAdded.class, tmpBluezProcessOwner, interfacesAddedHandler);
                }
                if (interfacesRemovedHandler != null) {
                    busConnection.addSigHandler(ObjectManager.InterfacesRemoved.class, tmpBluezProcessOwner, interfacesRemovedHandler);
                }
                if (propertiesChangedHandler != null) {
                    busConnection.addSigHandler(Properties.PropertiesChanged.class, tmpBluezProcessOwner, propertiesChangedHandler);
                }
                bluezProcessOwner = tmpBluezProcessOwner;
            }
        } catch (DBusException e) {
            throw new BluezException("Unable to access dbus to establish base bluetooth objects", e);
        }
    }

    void unbind() {
        if (bluezProcessOwner == null) {
            return;
        }

        try {
            synchronized (buslock) {
                if (interfacesAddedHandler != null) {
                    busConnection.removeSigHandler(ObjectManager.InterfacesAdded.class, bluezProcessOwner, interfacesAddedHandler);
                }
                if (propertiesChangedHandler != null) {
                    busConnection.removeSigHandler(Properties.PropertiesChanged.class, bluezProcessOwner, propertiesChangedHandler);
                }
                if (interfacesRemovedHandler != null) {
                    busConnection.removeSigHandler(ObjectManager.InterfacesRemoved.class, bluezProcessOwner, interfacesRemovedHandler);
                }
                bluezProcessOwner = null;
            }
        } catch (DBusException e) {
            logger.error("Unable to disable dbus signals, reason: {}", e.getMessage());
        }
    }

    public void rebind() {
        unbind();
        bind();
    }

    public DBusConnection getDbusConnection() {
        return busConnection;
    }

    public BluezAdapter getManagedAdapter(String path) throws BluezException {
        return getManagedAdapter(path, true);
    }

    public BluezAdapter getManagedAdapter(String path, boolean create) throws BluezException {
        BluezAdapter adapter = adapters.get(path);
        if (adapter != null) {
            return adapter;
        }

        if (!create) {
             //throw new BluezException("No such adapter managed: " + path);
             return null;
        }

        synchronized (this) {
            if (adapters.containsKey(path)) {
                return adapters.get(path);
            }

            adapters.putIfAbsent(path, new BluezAdapter(this, path));
            return adapters.get(path);
        }
    }

    public BluezAdapter getManagedAdapter(URL url) throws BluezException {
        // extremly ineffective :-/
        for (BluezAdapter adapter : adapters.values()) {
            if (url.getAdapterURL().equals(adapter.getURL())) {
                return adapter;
            }
        }
        
        return null;
    }

    public Collection<BluezAdapter> getManagedAdapters() {
        return adapters.values();
    }

    public synchronized void disposeAdapter(String path, boolean doRemoteCalls, boolean recurse) throws BluezException {
        String pathExpr = path + "/";
     
        Set<String> subdevices = devices.keySet().stream()
            .filter((devicePath) -> {
                return devicePath.startsWith(pathExpr);
            })
            .collect(Collectors.toSet());

        if (recurse) {
            for (String devicePath : subdevices) {
                disposeDevice(devicePath, doRemoteCalls, recurse);
            }
        }
        
        BluezAdapter adapter = adapters.get(path);
        if (adapter == null) {
            return;
        }

        BluezAdapter.dispose(adapter, doRemoteCalls, recurse);
        adapters.remove(path);
    }

    public BluezDevice getManagedDevice(String path) throws BluezException {
        return getManagedDevice(path, true);
    }

    public BluezDevice getManagedDevice(String path, boolean create) throws BluezException {
        BluezDevice device = devices.get(path);

        if (device != null) {
            return device;
        }

        if (!create) {
            logger.trace("{}: will not manage device", path);
            return null;
        }

        synchronized (this) {
            if (devices.containsKey(path)) {
                return devices.get(path);
            }

            logger.trace("{}: created handle for bluetooth device", path);
            devices.putIfAbsent(path, new BluezDevice(this, path));
            return devices.get(path);
        }
    }

    public BluezDevice getManagedDevice(URL url) throws BluezException {
	// consider getting better url?
        for (BluezDevice device : devices.values()) {
            if (url.getDeviceURL().equals(device.getURL())) {
                return device;
            }
        }
        
        return null;
    }

    public Collection<BluezDevice> getManagedDevices() {
        return devices.values();
    }

    public synchronized void disposeDevice(String path, boolean doRemoteCalls, boolean recurse) throws BluezException {
        String pathExpr = path + "/";
     
        Set<String> subcharacteristics = characteristics.keySet().stream()
            .filter((characteristicPath) -> {
                return characteristicPath.startsWith(pathExpr);
            })
            .collect(Collectors.toSet());

        if (recurse) {
            for (String characteristicPath : subcharacteristics) {
                disposeCharacteristic(characteristicPath, doRemoteCalls, recurse);
            }
        }
        
        BluezDevice device = (devices.get(path));
        if (device == null) {
            return;
        }

        BluezDevice.dispose(device, doRemoteCalls, recurse);
        devices.remove(path);
    }

    public BluezCharacteristic getManagedCharacteristic(String path) throws BluezException {
        return getManagedCharacteristic(path, true);
    }

    public BluezCharacteristic getManagedCharacteristic(String path, boolean create) throws BluezException {
        BluezCharacteristic characteristic = characteristics.get(path);
        if (characteristic != null) {
            return characteristic;
        }

        if (!create) {
            //throw new BluezException("No such characteristic managed: " + path);
            return null;
        }

        synchronized (this) {
            if (characteristics.containsKey(path)) {
                return characteristics.get(path);
            }

            logger.trace("{}: created handle for bluetooth characteristic", path);
            characteristics.putIfAbsent(path, new BluezCharacteristic(this, path));
            return characteristics.get(path);
        }
    }

    public BluezCharacteristic getManagedCharacteristic(URL url) throws BluezException {

        BluezDevice device = getManagedDevice(url);
        if (device == null) {
            logger.trace("Unable to access bluetooth service characteristic, as corresponding device is not managed: {}", url.toString());
            return null;
        }

        String devicePath = device.getPath();

        for (Map.Entry<String, BluezCharacteristic> entry : characteristics.entrySet()) {
            if (!entry.getKey().startsWith(devicePath)) {
                continue;
            }

            BluezCharacteristic characteristic = entry.getValue();
            if (characteristic.getUUID().equalsIgnoreCase(url.getCharacteristicUUID())) {
                return characteristic;
            }
        }

        logger.trace("Unable to access bluetooth service characteristic, as it is not managed: {}", url.toString());

        return null;
    }

    public synchronized void disposeCharacteristic(String path, boolean doRemoteCalls, boolean recurse) throws BluezException {
        BluezCharacteristic characteristic = characteristics.get(path);
        if (characteristic == null) {
            return;
        }

        BluezCharacteristic.dispose(characteristic, doRemoteCalls, recurse);
        characteristics.remove(path);
    }


    public synchronized void dispose() {
        for (BluezDevice device : devices.values()) {
            BluezDevice.dispose(device, true, true);
        }
        devices.clear();

        for (BluezAdapter adapter : adapters.values()) {
            BluezAdapter.dispose(adapter, true, true);
        }
        adapters.clear();

        unbind();
    } 
}
