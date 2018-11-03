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

import cz.organovabanka.bluetooth.manager.transport.dbus.BluezException;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezHooks;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezObject;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;

import org.freedesktop.DBus;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.interfaces.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;


/**
 * Context class for all Bluez related stuff
 * @author Lukas Rucka
 */
public class BluezContext {
    private static final Logger logger = LoggerFactory.getLogger(BluezContext.class);

    private DBusConnection busConnection;
    private String bluezProcessOwner;

    private DBusSigHandler<ObjectManager.InterfacesAdded> interfacesAddedHandler = null;
    private DBusSigHandler<ObjectManager.InterfacesRemoved> interfacesRemovedHandler = null;
    private DBusSigHandler<Properties.PropertiesChanged> propertiesChangedHandler = null;

    // concurrent, nebo copy-on-write?
    private Map<URL, BluezAdapter> adaptersByURL = new ConcurrentHashMap();
    private Map<URL, BluezDevice> devicesByURL = new ConcurrentHashMap();
    private Map<URL, BluezCharacteristic> characteristicsByURL = new ConcurrentHashMap();

    private ConcurrentNavigableMap<String, URL> pathURLMappings = new ConcurrentSkipListMap();

    private final BluezHooks hooks = new BluezHooks();

    private static final ExecutorService NOTIFICATION_SERVICE = Executors.newCachedThreadPool();

    public BluezContext() throws BluezException {
        connect();
    }

    public void connect() throws BluezException {
        try {
            busConnection = DBusConnection.getConnection(DBusConnection.DBusBusType.SYSTEM, false);
        } catch (DBusException e) {
            throw new BluezException("Unable to access dbus", e);
        }
    }

    synchronized private void reset() {
        pathURLMappings.clear();

        BluezCommons.runSilently(this::unbind);

        characteristicsByURL.entrySet().stream().forEach((e) -> BluezCommons.runSilently(e.getValue()::dispose));
        characteristicsByURL.clear();

        devicesByURL.entrySet().stream().forEach((e) -> BluezCommons.runSilently(e.getValue()::dispose));
        devicesByURL.clear();

        adaptersByURL.entrySet().stream().forEach((e) -> BluezCommons.runSilently(e.getValue()::dispose));
        adaptersByURL.clear();

        // should allready be disconnected
        BluezCommons.runSilently(busConnection::disconnect);

        connect();
        bind();
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
            synchronized (busConnection) {
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
            synchronized (busConnection) {
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

    public void notifySafely(Runnable noticator, Logger logger, String path) {
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
    private ExecutorService getNotificationService() {
        return NOTIFICATION_SERVICE;
    }   

    public DBusConnection getDbusConnection() {
        if (!busConnection.isConnected()) {
            logger.error("DBus connection went disconnected, attempting to reinitialize!");
            reset();
        }
        // todo livecheck
        return busConnection;
    }

    public BluezAdapter getManagedAdapter(URL url) {
        return adaptersByURL.get(url);
    }

    public void disposeAdapter(URL adapterURL) {
        BluezAdapter adapter = adaptersByURL.get(adapterURL);
        if (adapter == null) {
            return;
        }
    
        adapter.dispose();
    }

    public BluezAdapter emplaceAdapter(URL url, Supplier<BluezAdapter> adapterConstructor) {
        synchronized (adaptersByURL) {
            BluezAdapter adapter = adaptersByURL.get(url);
            if (adapter != null) {
                return adapter;
            }

            adapter = adapterConstructor.get();
            adaptersByURL.put(url, adapter);
            return adapter;
        }
    }

    public Collection<BluezAdapter> getManagedAdapters() {
        return adaptersByURL.values();
    }

    public void dropAdapter(URL adapterURL) {
        BluezAdapter adapter = adaptersByURL.get(adapterURL);
        adaptersByURL.remove(adapterURL);

        if (!(adapter instanceof NativeBluezObject)) {
            return;
        }

        NativeBluezObject dbusAdapter = (NativeBluezObject)adapter;
        String dbuspath = dbusAdapter.getPath();

        Set<String> paths = new HashSet<>();
        Map<String, URL> keyEntry = pathURLMappings.subMap(dbuspath + ":", true, dbuspath + ";", false);
        for (Map.Entry<String, URL> e : keyEntry.entrySet()) {
            if (!e.getKey().startsWith(dbuspath + ":")) {
                continue;
            }

            if (!e.getValue().equals(adapterURL)) {
                continue;
            }

            paths.add(e.getKey());
        }

        for (String key : paths) {
            pathURLMappings.remove(key);
        }
    }    

    public BluezDevice getManagedDevice(URL url) {
        return devicesByURL.get(url);
    }

    public void disposeDevice(URL deviceURL) {
        BluezDevice device = devicesByURL.get(deviceURL);
        if (device == null) {
            return;
        }
    
        device.dispose();
    }

    public BluezDevice emplaceDevice(URL url, Supplier<BluezDevice> deviceConstructor) {
        synchronized (devicesByURL) {
            BluezDevice device = devicesByURL.get(url);
            if (device != null) {
                return device;
            }

            device = deviceConstructor.get();
            devicesByURL.put(url, device);
            return device;
        }
    }

    public Collection<BluezDevice> getManagedDevices() {
        return devicesByURL.values();
    }

    public void dropDevice(URL deviceURL) {
        BluezDevice device = devicesByURL.get(deviceURL);
        devicesByURL.remove(deviceURL);

        if (!(device instanceof NativeBluezObject)) {
            return;
        }

        NativeBluezObject dbusDevice = (NativeBluezObject)device;
        String dbuspath = dbusDevice.getPath();

        Set<String> paths = new HashSet<>();
        Map<String, URL> keyEntry = pathURLMappings.subMap(dbuspath + ":", true, dbuspath + ";", false);
        for (Map.Entry<String, URL> e : keyEntry.entrySet()) {
            if (!e.getKey().startsWith(dbuspath + ":")) {
                continue;
            }

            if (!e.getValue().equals(deviceURL)) {
                continue;
            }

            paths.add(e.getKey());
        }

        for (String key : paths) {
            pathURLMappings.remove(key);
        }
    }    

    public BluezCharacteristic getManagedCharacteristic(URL url) {
        return characteristicsByURL.get(url);
    }
    
    public void disposeCharacteristic(URL characteristicURL) {
        BluezCharacteristic characteristic = characteristicsByURL.get(characteristicURL);
        if (characteristic == null) {
            return;
        }
    
        characteristic.dispose();
    }
  
    public BluezCharacteristic emplaceCharacteristic(URL url, Supplier<BluezCharacteristic> characteristicConstructor) {
        synchronized (characteristicsByURL) {
            BluezCharacteristic characteristic = characteristicsByURL.get(url);
            if (characteristic != null) {
                return characteristic;
            }

            characteristic = characteristicConstructor.get();
            characteristicsByURL.put(url, characteristic);
            return characteristic;
        }
    }
    
    public void dropCharacteristic(URL characteristicURL) {
        BluezCharacteristic characteristic = characteristicsByURL.get(characteristicURL);
        characteristicsByURL.remove(characteristicURL);

        if (!(characteristic instanceof NativeBluezObject)) {
            return;
        }

        NativeBluezObject dbusCharacteristic = (NativeBluezObject)characteristic;
        String dbuspath = dbusCharacteristic.getPath();

        Set<String> paths = new HashSet<>();
        Map<String, URL> keyEntry = pathURLMappings.subMap(dbuspath + ":", true, dbuspath + ";", false);
        for (Map.Entry<String, URL> e : keyEntry.entrySet()) {
            if (!e.getKey().startsWith(dbuspath + ":")) {
                continue;
            }

            if (!e.getValue().equals(characteristicURL)) {
                continue;
            }

            paths.add(e.getKey());
        }

        for (String key : paths) {
            pathURLMappings.remove(key);
        }
    }    

    public synchronized void dispose() {
        unbind();

        synchronized (characteristicsByURL) {
            characteristicsByURL.values().stream()
                .filter((characteristic) -> characteristic.isActive())
                .forEach((characteristic) -> characteristic.dispose());
            characteristicsByURL.clear();
        }

        synchronized (devicesByURL) {
            devicesByURL.values().stream()
                .filter((device) -> device.isActive())
                .forEach((device) -> device.dispose());
            devicesByURL.clear();
        }

        synchronized (adaptersByURL) {
            adaptersByURL.values().stream()
                .filter((adapter) -> adapter.isActive())
                .forEach((adapter) -> adapter.dispose());
            adaptersByURL.clear();
        }
    } 

    public BluezHooks getHooks() {
        return hooks;
    }

    public URL pathURL(String iface, String path) {
        return pathURLMappings.get(path + ":" + iface);
    }

    public URL pushPathURL(String iface, String path, URL url) {
        return pathURLMappings.put(path + ":" + iface, url);
    }

}
