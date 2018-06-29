package cz.organovabanka.bluetooth.manager.transport.dbus;

/*-
 * #%L
 * org.sputnikdev:bluetooth-manager-dbus
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

import org.freedesktop.DBus;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Adapter;
import org.sputnikdev.bluetooth.manager.transport.Device;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import java.lang.Short;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Adapter1;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;

/**
 * A class representing Bluez adapters.
 * @author Lukas Rucka
 */
class BluezAdapter extends BluezObjectBase implements Adapter {

    private static final Logger logger = LoggerFactory.getLogger(BluezAdapter.class);

    private final Adapter1 remoteInterface;

    private Notification<Boolean> notificationDiscovering = null;
    private Notification<Boolean> notificationPowered = null;

    BluezAdapter(BluezContext context, String dbusObjectPath) throws BluezException {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_ADAPTER);

        try {
            this.remoteInterface = busConnection.getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Adapter1.class);
        } catch (DBusException e) {
            throw new BluezException("Unable to access dbus objects for " + dbusObjectPath, e); 
        }

        cache.set("Powered", new Boolean(false));
        cache.set("Discovering", new Boolean(false));
        cache.set("Discoverable", new Boolean(false));

        cache.set("Address", "XX:XX:XX:XX:XX:XX");

        cache.set("Alias", "Unknown");
        cache.set("Name", "Unknown");

        cache.set("Class", new UInt32(0));

        cache.set("url", BluezCommons.DBUSB_PROTOCOL_NAME + "://XX:XX:XX:XX:XX:XX/YY:YY:YY:YY:YY:YY");

        setupHandlers();
        updateURL();
    }

    private void setupHandlers() throws BluezException {
        this.handlers.put("Powered", (powered) -> {
            if (notificationPowered == null) {
                return;
            }

            BluezFactory.notifySafely(() -> {
                notificationPowered.notify( (Boolean)(powered.getValue()) );
            }, logger, dbusObjectPath + ":Powered");
        });
        this.handlers.put("Discovering", (discovering) -> {
            if (notificationDiscovering == null) {
                return;
            }

            BluezFactory.notifySafely(() -> {
                notificationDiscovering.notify((Boolean)discovering.getValue());
            }, logger, dbusObjectPath + ":Discovering");
        });
    }

    protected Logger getLogger() {
        return logger;
    }

    /* dbus & openhab handles */

    protected void updateURL() {
        try {
            cache.set("url", BluezCommons.DBUSB_PROTOCOL_NAME + "://" + getAddress());
        } catch (BluezException e) {
            getLogger().error("{}: Unable to update URL, reason: {}", dbusObjectPath, e.getMessage());
        }
    }

    /* begin remote adapter methods */

    private void stopDiscoveryRemote() throws BluezException {
        if (!allowRemoteCalls) {
            getLogger().debug("{}: Not allowed to make remote calls", dbusObjectPath);
            return;
        }

        try {
            callWithDispose(
                () -> { remoteInterface.StopDiscovery(); },
                () -> { allowRemoteCalls = false; context.disposeAdapter(dbusObjectPath, false, true); }
            );
        } catch (DBusExecutionException e) {
            if (e.getMessage().indexOf("No discovery started") >= 0) {
                getLogger().error("{}: Stopping discovery failed with (ignored) error: {}", dbusObjectPath, e.getMessage());
		// stop discovery
                cache.set("Discovering", false);
                return;
            } else if (e.getMessage().indexOf("in progress") >= 0) {
                getLogger().error("{}: Stopping discovery failed with (ignored) error: {}", dbusObjectPath, e.getMessage());
                return;
            }

            throw new BluezException("Unable to stop bluetooth discovery on adapter " + dbusObjectPath + " (" + e.getMessage() + ")", e);
        } catch (RuntimeException e) {
            throw new BluezException("Unable to call stopDiscovery on " + dbusObjectPath + ": " + e.getMessage(), e); 
        }
    }   

    @Override
    public boolean stopDiscovery() throws BluezException {
        stopDiscoveryRemote();
        // if we got here, than stopDiscovery() was successfuly called, and therefore
        // cache.set("Discovering", false);
        // consider integrating todo above
        return !isDiscovering();
    }

    private void startDiscoveryRemote() throws BluezException {
        if (!allowRemoteCalls) {
            getLogger().debug("{}: Not allowed to make remote calls", dbusObjectPath);
            return;
        }

        
        Map<String, Variant> filterOptions = new HashMap<String, Variant>();
        filterOptions.put("RSSI", new Variant<Short>(new Short((short)-100), "n"));

        try {
            callWithDispose(
                () -> { remoteInterface.SetDiscoveryFilter(filterOptions); },
                () -> { allowRemoteCalls = false; context.disposeAdapter(dbusObjectPath, false, true); }
            );
        } catch (RuntimeException e) {
            getLogger().error("{}: Failed to apply bluetooth discovery filter, reason: {}", dbusObjectPath, e.getMessage()); 
        }

        try {
            callWithDispose(
                () -> { remoteInterface.StartDiscovery(); },
                () -> { allowRemoteCalls = false; context.disposeAdapter(dbusObjectPath, false, true); }
            );
        } catch (DBusExecutionException e) {
            if (e.getMessage().indexOf("in progress") >= 0) {
                getLogger().error("{}: Bluetooth discovery allready pending: {}", dbusObjectPath, e.getMessage()); 
                return;
            }

            throw new BluezException("Unable to start bluetooth discovery on adapter " + dbusObjectPath + " (" + e.getMessage() + ")", e);
        } catch (RuntimeException e) {
            throw new BluezException("Unable to call startDiscovery on " + dbusObjectPath + ": " + e.getMessage(), e); 
        }
    }  
 
    @Override
    public boolean startDiscovery() throws BluezException {
        startDiscoveryRemote();
        return isDiscovering();
    }

    public void removeDevice(String devicePath) {
        getLogger().debug("{}: Remove device {}", dbusObjectPath, devicePath);
        try {
            remoteInterface.RemoveDevice(new Path(devicePath));
        } catch (RuntimeException e) { 
            getLogger().error("{}: Failed to remove device {}, reason: {}", dbusObjectPath, devicePath, e.getMessage()); 
        }
    }

    /* notification setters */

    @Override
    public void enablePoweredNotifications(Notification<Boolean> notification) {
        //getLogger().trace("{}: Enable powered notifications", dbusObjectPath);
        notificationPowered = notification;
    }

    @Override
    public void disablePoweredNotifications() {
        //getLogger().trace("{}: Disable powered notifications", dbusObjectPath);
        notificationPowered = null;
    }

    @Override
    public void enableDiscoveringNotifications(Notification<Boolean> notification) {
        //getLogger().trace("{}: Enable discovering notifications", dbusObjectPath);
        notificationDiscovering = notification;
    }

    @Override
    public void disableDiscoveringNotifications() {
        //getLogger().trace("{}: Disable discovering notifications", dbusObjectPath);
        notificationDiscovering = null;
    }

    /* subtree list */

    @Override
    public List<Device> getDevices() throws BluezException {
        Pattern devicePattern = Pattern.compile("^" + this.dbusObjectPath + "/dev(_[0-9a-fA-F]{2}){6}$");

        Map<Path, Map<String, Map<String, Variant>>> allObjects = null;
        try {
            ObjectManager objectManager = busConnection.getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
            allObjects = objectManager.GetManagedObjects();
        } catch (DBusException e) {
            throw new BluezException("Unable to enumerate bluetooth objects when processing " + dbusObjectPath, e);
        } catch (RuntimeException e) {
            throw new BluezException("Unable to enumerate bluetooth objects when processing " + dbusObjectPath, e);
        }

        try {
            List<BluezDevice> allDevices = allObjects.entrySet().stream()
                .map((entry) -> { return entry.getKey().toString(); })
                .filter((path) -> { return devicePattern.matcher(path).matches(); })
                .map((path) -> { return context.getManagedDevice(path, true); })
                .filter((device) -> { return (device.getRSSI() != 0); })
                .collect(Collectors.toList());
            return Collections.unmodifiableList(allDevices);
        } catch (RuntimeException e) {
            throw new BluezException("Unable to unpack bluez objects when processing " + dbusObjectPath, e);
        }
    }

    protected void disposeRemote() {
        // remote part
        if (!allowRemoteCalls) {
            return;
        }

        stopDiscoveryRemote();
    }

    protected void disposeLocal(boolean doRemoteCalls, boolean recurse) {
        // local part

        // first disable notifications
        disableDiscoveringNotifications();
        disablePoweredNotifications();
        // todo: consider recursive on devices
    }

    public static void dispose(BluezAdapter obj, boolean doRemoteCalls, boolean recurse) { 
        logger.debug("{}:{} Disposing characteristic", obj.dbusObjectPath, obj.getURL().getAdapterAddress());
        BluezObjectBase.dispose(obj, doRemoteCalls, recurse);
    }

    /* access attributes */

    private void getAddressRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<String>attemptCachedPropertyUpdate("Address");
    }

    public String getAddress() {
        // call remote part
        if (allowRemoteCalls) {
            getAddressRemote();
        }
        // local part
        return cache.<String>get("Address");
    }  

    private void getAliasRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<String>attemptCachedPropertyUpdate("Alias");
    }   

    @Override
    public String getAlias() {
        // call remote part
        if (allowRemoteCalls) {
            getAliasRemote();
        }
        // local part
        return cache.<String>get("Alias");
    }  

    private void setAliasRemote(String alias) throws BluezException {
        // remote - update cache
        // property - no action if write fails
        try {
            this.<String>writeProperty(primaryInterface, "Alias", alias);
        } catch (DBusException e) {
            getLogger().error("{}:{} Failed to write property, reason: {}", dbusObjectPath, "Alias", e.getMessage());
            throw new BluezException("Failed to update alias on " + dbusObjectPath);
        }
        // do not update cache just yet
    }   

    @Override
    public void setAlias(String alias) throws BluezException {
        // call remote part
        if (allowRemoteCalls) {
            setAliasRemote(alias);
        }
        // local part - NOP
    }  

    private void getNameRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<String>attemptCachedPropertyUpdate("Name");
    }   

    @Override
    public String getName() {
        // call remote part
        if (allowRemoteCalls) {
            getNameRemote();
        }
        // local part
        return cache.<String>get("Name");
    }  

    /* link quality attributes */

    private void getBluetoothClassRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<UInt32>attemptCachedPropertyUpdate("Class");
    }   

    public int getBluetoothClass() {
        // call remote part
        if (allowRemoteCalls) {
            getBluetoothClassRemote();
        }
        // local part
        return cache.<UInt32>get("Class").intValue();
    }  

    /* discovery related attributes */

    private void isDiscoveringRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Boolean>attemptCachedPropertyUpdate("Discovering");
    }   

    @Override
    public boolean isDiscovering() {
        // call remote part
        if (allowRemoteCalls) {
            isDiscoveringRemote();
        }
        // local part
        return cache.<Boolean>get("Discovering").booleanValue();
    }  


    private void isPoweredRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Boolean>attemptCachedPropertyUpdate("Powered");
    }   

    @Override
    public boolean isPowered() {
        // call remote part
        if (allowRemoteCalls) {
            isPoweredRemote();
        }
        // local part
        return cache.<Boolean>get("Powered").booleanValue();
    }  

    private void setPoweredRemote(boolean powered) throws BluezException {
        // remote - update cache
        // property - no action if write fails
        try {
            this.<Boolean>writeProperty(primaryInterface, "Powered", powered);
        } catch (DBusException e) {
            getLogger().error("{}:{} Failed to write property, reason: {}", dbusObjectPath, "Alias", e.getMessage());
            throw new BluezException("Failed to power device " + dbusObjectPath, e);
        }   
        // do not update cache just yet
    }   

    @Override
    public void setPowered(boolean powered) throws BluezException {
        // local is a bit complicated
        try {
            // bluetooth manager calls power down to "reset" angry devices
            if (powered) {
                Thread.sleep(500);
            }
        } catch (InterruptedException e) { 
            /* silently ignore */
        }

        // call remote part
        if (allowRemoteCalls) {
            setPoweredRemote(powered);
        }

        try {
            if (!powered) {
                Thread.sleep(500);
            }
        } catch (InterruptedException e) { 
            /* silently ignore */
        }
    }  

    private void isDiscoverableRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Boolean>attemptCachedPropertyUpdate("Discoverable");
    }   

    public boolean isDiscoverable() {
        // call remote part
        if (allowRemoteCalls) {
            isDiscoverableRemote();
        }
        // local part
        return cache.<Boolean>get("Discoverable").booleanValue();
    }  

    private void setDiscoverableRemote(boolean powered) throws BluezException {
        // remote - update cache
        // property - no action if write fails
        try {
            this.<Boolean>writeProperty(primaryInterface, "Discoverable", powered);
        } catch (DBusException e) {
            getLogger().error("{}:{} Failed to write property, reason: {}", dbusObjectPath, "Discoverable", e.getMessage());
            throw new BluezException("Failed to (un)set discoverable for " + dbusObjectPath, e);
        }   
        // do not update cache just yet
    }   

    public void setDiscoverable(boolean powered) throws BluezException {
        // call remote part
        if (allowRemoteCalls) {
            setDiscoverableRemote(powered);
        }
        // local - NOP
    }  

}
