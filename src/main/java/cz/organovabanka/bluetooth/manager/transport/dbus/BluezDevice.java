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
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt16;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.DataConversionUtils;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothAddressType;
import org.sputnikdev.bluetooth.manager.transport.Device;
import org.sputnikdev.bluetooth.manager.transport.Notification;
import org.sputnikdev.bluetooth.manager.transport.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Device1;

/**
 * A class representing Bluez devices.
 * @author Lukas Rucka
 */
class BluezDevice extends BluezObjectBase implements Device {
    private static final Logger logger = LoggerFactory.getLogger(BluezDevice.class);

    private final Device1 remoteInterface;

    private Notification<Short> notificationRssi = null;
    private Notification<Boolean> notificationBlocked = null;
    private Notification<Boolean> notificationConnected = null;
    private Notification<Boolean> notificationServicesResolved = null;
    private Notification<Map<String, byte[]>> notificationServiceData = null;
    private Notification<Map<Short, byte[]>> notificationManufacturerData = null;

    BluezDevice(BluezContext context, String dbusObjectPath) throws BluezException {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_DEVICE);

        // setup default values of cached attributes
        // todo following
        try {
            this.remoteInterface = busConnection.getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Device1.class);
        } catch (DBusException e) {
            throw new BluezException("Unable to access dbus objects for " + dbusObjectPath, e); 
        }

        cache.set("Blocked", new Boolean(false));
        cache.set("Connected", new Boolean(false));
        cache.set("ServicesResolved", new Boolean(false));

        cache.set("AddressType", "UNKNOWN");
        cache.set("Address", "YY:YY:YY:YY:YY:YY");
        cache.set("Adapter", "/org/bluez/hciX");

        cache.set("Alias", "Unknown");
        cache.set("Name", "Unknown");

        cache.set("Class", new UInt32(0));
        cache.set("RSSI", new Short((short)-100));
        cache.set("TxPower", new Short((short)-100));

        cache.set("url", BluezCommons.DBUSB_PROTOCOL_NAME + "://XX:XX:XX:XX:XX:XX/YY:YY:YY:YY:YY:YY");

        setupHandlers();
        updateURL();
    }

    private void setupHandlers() {
        this.handlers.put("RSSI", (rssi) -> {
            if (notificationRssi == null) {
                return;
            }

            BluezFactory.notifySafely(
                () -> { notificationRssi.notify(((Short)rssi.getValue()).shortValue()); }, 
                getLogger(), dbusObjectPath + ":RSSI");
        });

        this.handlers.put("Blocked", (blocked) -> {
            if (notificationBlocked == null) {
                return;
            }

            BluezFactory.notifySafely(
                () -> { notificationBlocked.notify(((Boolean)blocked.getValue()).booleanValue()); }, 
                getLogger(), dbusObjectPath + ":Blocked"); 
        });
        this.handlers.put("Connected", (connected) -> {
            if (notificationConnected == null) {
                return;
            }

            boolean value = ((Boolean)connected.getValue()).booleanValue();

            BluezFactory.notifySafely(
                () -> { notificationConnected.notify(value); }, 
                getLogger(), dbusObjectPath + ":Connected");
        });
        this.handlers.put("ServicesResolved", (resolved) -> {
            if (notificationServicesResolved == null) {
                return;
            }

            boolean value = (Boolean)resolved.getValue();

            BluezFactory.notifySafely(
                () -> { notificationServicesResolved.notify(value); },
                getLogger(), dbusObjectPath + ":ServicesResolved");
        });

        this.handlers.put("ServiceData", (data) -> {
            if (notificationServiceData == null) {
                return;
            }

            Map<String, byte[]> rawData = convertServiceData((Map<String, Variant>)data.getValue());
            if (getLogger().isTraceEnabled()) {
                getLogger().trace("{}: Service data changed: {}", dbusObjectPath, hexdump(rawData));
            }

            BluezFactory.notifySafely(
                () -> { notificationServiceData.notify(rawData); },
                getLogger(), dbusObjectPath + ":ServiceData");
        });
        this.handlers.put("ManufacturerData", (data) -> {
            if (notificationManufacturerData == null) {
                return;
            }

            Map<Short, byte[]> rawData = convertManufacturerData((Map<UInt16, Variant>)data.getValue());
            if (getLogger().isTraceEnabled()) {
                getLogger().trace("{}: Manufacturer data changed: {}", dbusObjectPath, hexdump(rawData));
            }

            BluezFactory.notifySafely(
                () -> { notificationManufacturerData.notify(rawData); }, 
                getLogger(), dbusObjectPath + ":ManufacturerData");

        });
    } 

    protected Logger getLogger() {
        return logger;
    }

    /* dbus & openhab handles */

    protected void updateURL() throws BluezException {
        try {
            String adapterPath = getAdapterPath();
            BluezAdapter adapter = context.getManagedAdapter(adapterPath, false);
            URL url = adapter.getURL().copyWithDevice(getAddress());
            cache.set("url", url.toString());
        } catch (BluezException e) {
            getLogger().error("{}: Unable to update URL, reason: {}", dbusObjectPath, e.getMessage());
        }
    }

    public String getAdapterPath() {
        // local part only
        return BluezCommons.parsePath(dbusObjectPath, BluezAdapter.class);
    }

    /* begin remote device methods */

    private void disconnectRemote() throws BluezException {
        if (!allowRemoteCalls) {
            getLogger().debug("{}: Not allowed to make remote calls", dbusObjectPath);
            return;
        }

        try {
            callWithDispose(
                () -> { remoteInterface.Disconnect(); },
                () -> { allowRemoteCalls = false; context.disposeDevice(dbusObjectPath, false, true); }
            );
        } catch (RuntimeException e) {
            throw new BluezException("Unable to call disconnect on " + dbusObjectPath + ": " + e.getMessage(), e); 
        }
    }   

    @Override
    public boolean disconnect() throws BluezException {
        getLogger().trace("{}: requested disconnect", dbusObjectPath);
        disconnectRemote();
        // if we got here, than disconnect() was successfuly called, and therefore
        // cache.set("Connected", false);
        return !isConnected();
    }

/*
2018-05-24 08:25:10.997 [INFO ] [h.manager.transport.dbus.BluezDevice] - Submitting notification on /org/bluez/hci1/dev_7C_2F_80_B1_87_25:ServicesResolved
2018-05-24 08:25:11.001 [INFO ] [h.manager.transport.dbus.BluezDevice] - Submitting notification on /org/bluez/hci1/dev_7C_2F_80_B1_87_25:Connected
2018-05-24 08:25:11.024 [WARN ] [impl.AbstractBluetoothObjectGovernor] - Error occurred while interacting (read) with native object: /00:1A:7D:DA:71:16/7C:2F:80:B1:87:2
5/0000180a-0000-1000-8000-00805f9b34fb/00002a28-0000-1000-8000-00805f9b34fb : false : Unable to read value of /org/bluez/hci1/dev_7C_2F_80_B1_87_25/service0010/char0017
: Not connected
2018-05-24 08:25:11.029 [WARN ] [anager.impl.CompletableFutureService] - Bluetooth error happened while competing a future immediately: /XX:XX:XX:XX:XX:XX/7C:2F:80:B1:8
7:25/0000180a-0000-1000-8000-00805f9b34fb/00002a28-0000-1000-8000-00805f9b34fb : Error occurred while interacting (read) with native object: /00:1A:7D:DA:71:16/7C:2F:80
:B1:87:25/0000180a-0000-1000-8000-00805f9b34fb/00002a28-0000-1000-8000-00805f9b34fb : false : Unable to read value of /org/bluez/hci1/dev_7C_2F_80_B1_87_25/service0010/
char0017: Not connected
2018-05-24 08:25:11.047 [WARN ] [impl.AbstractBluetoothObjectGovernor] - Error occurred while interacting (read) with native object: /00:1A:7D:DA:71:16/7C:2F:80:B1:87:2
5/0000180a-0000-1000-8000-00805f9b34fb/00002a29-0000-1000-8000-00805f9b34fb : false : Unable to read value of /org/bluez/hci1/dev_7C_2F_80_B1_87_25/service0010/char0011
: Not connected
2018-05-24 08:25:11.059 [WARN ] [impl.AbstractBluetoothObjectGovernor] - Error occurred while interacting (read) with native object: /00:1A:7D:DA:71:16/7C:2F:80:B1:87:2
5/0000180a-0000-1000-8000-00805f9b34fb/00002a24-0000-1000-8000-00805f9b34fb : false : Unable to read value of /org/bluez/hci1/dev_7C_2F_80_B1_87_25/service0010/char0013
: Not connected
2018-05-24 08:25:11.062 [WARN ] [anager.impl.CompletableFutureService] - Bluetooth error happened while competing a future immediately: /XX:XX:XX:XX:XX:XX/7C:2F:80:B1:8
7:25/0000180a-0000-1000-8000-00805f9b34fb/00002a29-0000-1000-8000-00805f9b34fb : Error occurred while interacting (read) with native object: /00:1A:7D:DA:71:16/7C:2F:80
:B1:87:25/0000180a-0000-1000-8000-00805f9b34fb/00002a29-0000-1000-8000-00805f9b34fb : false : Unable to read value of /org/bluez/hci1/dev_7C_2F_80_B1_87_25/service0010/
char0011: Not connected
2018-05-24 08:25:11.065 [WARN ] [anager.impl.CompletableFutureService] - Bluetooth error happened while competing a future immediately: /XX:XX:XX:XX:XX:XX/7C:2F:80:B1:8
7:25/0000180a-0000-1000-8000-00805f9b34fb/00002a24-0000-1000-8000-00805f9b34fb : Error occurred while interacting (read) with native object: /00:1A:7D:DA:71:16/7C:2F:80
:B1:87:25/0000180a-0000-1000-8000-00805f9b34fb/00002a24-0000-1000-8000-00805f9b34fb : false : Unable to read value of /org/bluez/hci1/dev_7C_2F_80_B1_87_25/service0010/
char0013: Not connected
*/


    private void connectRemote() throws BluezException {
        if (!allowRemoteCalls) {
            // todo debug
            getLogger().error("{}: Not allowed to make remote calls", dbusObjectPath);
            return;
        }

        try {
            callWithDispose(
                () -> { remoteInterface.Connect(); },
                () -> { allowRemoteCalls = false; context.disposeDevice(dbusObjectPath, false, true); }
            );
        } catch (RuntimeException e) {
            throw new BluezException("Unable to connect to " + dbusObjectPath + ": " + e.getMessage(), e); 
        }
    }  
 
    @Override
    public boolean connect() throws BluezException {
        getLogger().trace("{}: requested device connect", dbusObjectPath);
        connectRemote();
        return isConnected();
    }

    /* notification setters */

    @Override
    public void enableBlockedNotifications(Notification<Boolean> notification) {
        //getLogger().trace("{}:Blocked: Enable notifications", dbusObjectPath);
        notificationBlocked = notification;
    }

    @Override
    public void disableBlockedNotifications() {
        //getLogger().trace("{}:Blocked: Disable notifications", dbusObjectPath);
        notificationBlocked = null;
    }

    @Override
    public void enableRSSINotifications(Notification<Short> notification) {
        //getLogger().trace("{}:RSSI: Enable notifications", dbusObjectPath);
        notificationRssi = notification;
    }

    @Override
    public void disableRSSINotifications() {
        //getLogger().trace("{}:RSSI: Disable notifications", dbusObjectPath);
        notificationRssi = null;
    }

    @Override
    public void enableConnectedNotifications(Notification<Boolean> notification) {
        //getLogger().trace("{}:Connected: Enable notifications", dbusObjectPath);
        notificationConnected = notification;
    }

    @Override
    public void disableConnectedNotifications() {
        //getLogger().trace("{}:Connected: Disable notifications", dbusObjectPath);
        notificationConnected = null;
    }

    @Override
    public void enableServicesResolvedNotifications(Notification<Boolean> notification) {
        //getLogger().trace("{}:ServicesResolved: Enable notifications", dbusObjectPath);
        notificationServicesResolved = notification;
    }

    @Override
    public void disableServicesResolvedNotifications() throws BluezException {
        //getLogger().trace("{}:ServicesResolved: Disable notifications", dbusObjectPath);
        notificationServicesResolved = null;
    }

    @Override
    public void enableServiceDataNotifications(Notification<Map<String, byte[]>> notification) {
        //getLogger().trace("{}:ServiceData: Enable notifications", dbusObjectPath);
        notificationServiceData = notification;
    }

    @Override
    public void disableServiceDataNotifications() {
        //getLogger().trace("{}:ServiceData: Disable notifications", dbusObjectPath);
        notificationServiceData = null;
    }

    @Override
    public void enableManufacturerDataNotifications(Notification<Map<Short, byte[]>> notification) {
        //getLogger().trace("{}:ManufacturerData: Enable notifications", dbusObjectPath);
        notificationManufacturerData = notification;
    }

    @Override
    public void disableManufacturerDataNotifications() {
        //getLogger().trace("{}:ManufacturerData: Disable notifications", dbusObjectPath);
        notificationManufacturerData = null;
    }

    /* subtree list */

    @Override
    public List<Service> getServices() throws BluezException {
        getLogger().trace("{}: Listing resolved services", dbusObjectPath);

        if (!isConnected()) {
            return Collections.emptyList();
        }

        Pattern servicePattern = Pattern.compile(
            "^" + this.dbusObjectPath + "/service[0-9a-fA-F]{4}$");

        Map<Path, Map<String, Map<String, Variant>>> allObjects = null;
        try {
            ObjectManager objectManager = busConnection.getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
            allObjects = objectManager.GetManagedObjects();
        } catch (DBusException e) {
            throw new BluezException("Unable to enumerate bluetooth objects when processing " + dbusObjectPath, e);
        } catch (RuntimeException e) {
            throw new BluezException("Unable to enumerate bluetooth objects when processing " + dbusObjectPath, e);
        }

        if (allObjects == null) {
            return Collections.emptyList();
        }

        try {
            return Collections.unmodifiableList(allObjects.entrySet().stream()
                .map((entry) -> { return entry.getKey().toString(); })
                .filter((path) -> { return servicePattern.matcher(path).matches(); })
                .map((path) -> { return new BluezService(context, path); })
                .collect(Collectors.toList()));
        } catch (RuntimeException e) {
            throw new BluezException("Unable to unpack bluez objects when processing " + dbusObjectPath, e);
        }
    }

    protected void disposeRemote() {
        // remote part
        if (!allowRemoteCalls) {
            return;
        }

        disconnectRemote();
    }

    protected void disposeLocal(boolean doRemoteCalls, boolean recurse) {
        // local part

        // first disable notifications
        disableBlockedNotifications();
        disableConnectedNotifications();
        disableRSSINotifications();
        disableServicesResolvedNotifications();
        disableManufacturerDataNotifications();
        disableServiceDataNotifications();
    }

    public static void dispose(BluezDevice obj, boolean doRemoteCalls, boolean recurse) { 
        logger.trace("{}: Disposing device ({}/{})", obj.dbusObjectPath, obj.getURL().getAdapterAddress(), obj.getURL().getDeviceAddress());
        BluezObjectBase.dispose(obj, doRemoteCalls, recurse);
    }

    /* access attributes */

    private void getAddressTypeRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<String>attemptCachedPropertyUpdate("AddressType");
    }

    private BluetoothAddressType parseAddressType(String addressType) {
        if ("public".equals(addressType)) {
            return BluetoothAddressType.PUBLIC;
        } else if ("random".equals(addressType)) {
            return BluetoothAddressType.RANDOM;
        } else {
            return BluetoothAddressType.UNKNOWN;
        }
    }

    @Override
    public BluetoothAddressType getAddressType() {
        // call remote part
        getAddressTypeRemote();

        // local part
        String addressType = cache.<String>get("AddressType");
        return parseAddressType(addressType);
    }

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
        return this.cache.<String>get("Address");
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
        return this.cache.<String>get("Alias");
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
        return this.cache.<String>get("Name");
    }  

    /* link quality attributes */

    private void getTxPowerRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Short>attemptCachedPropertyUpdate("TxPower");
    }   

    @Override
    public short getTxPower() {
        // call remote part
        if (allowRemoteCalls) {
            getTxPowerRemote();
        }
        // local part
        return this.cache.<Short>get("TxPower").shortValue();
    }  

    private void getRSSIRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Short>attemptCachedPropertyUpdate("RSSI");
    }   

    @Override
    public short getRSSI() {
        // call remote part
        if (allowRemoteCalls) {
            getRSSIRemote();
        }
        // local part
        return this.cache.<Short>get("RSSI").shortValue();
    }  

    private void getBluetoothClassRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<UInt32>attemptCachedPropertyUpdate("Class");
    }   

    @Override
    public int getBluetoothClass() {
        // call remote part
        if (allowRemoteCalls) {
            getBluetoothClassRemote();
        }
        // local part
        return this.cache.<UInt32>get("Class").intValue();
    }  

    /* connection related attributes */

    private void isConnectedRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Boolean>attemptCachedPropertyUpdate("Connected");
    }   

    @Override
    public boolean isConnected() {
        // call remote part
        if (allowRemoteCalls) {
            isConnectedRemote();
        }
        // local part
        return this.cache.<Boolean>get("Connected").booleanValue();
    }  

    private void isTrustedRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Boolean>attemptCachedPropertyUpdate("Trusted");
    }   

    public boolean isTrusted() {
        // call remote part
        if (allowRemoteCalls) {
            isTrustedRemote();
        }
        // local part
        return this.cache.<Boolean>get("Trusted").booleanValue();
    }  

    private void isPairedRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Boolean>attemptCachedPropertyUpdate("Paired");
    }   

    public boolean isPaired() {
        // call remote part
        if (allowRemoteCalls) {
            isPairedRemote();
        }
        // local part
        return this.cache.<Boolean>get("Paired").booleanValue();
    }  

    private void isBlockedRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Boolean>attemptCachedPropertyUpdate("Blocked");
    }   

    @Override
    public boolean isBlocked() {
        // call remote part
        if (allowRemoteCalls) {
            isBlockedRemote();
        }
        // local part
        return this.cache.<Boolean>get("Blocked").booleanValue();
    }  

    private void setBlockedRemote(boolean blocked) throws BluezException {
        // remote - update cache
        // property - no action if write fails
        try {
            this.<Boolean>writeProperty(primaryInterface, "Blocked", blocked);
        } catch (DBusException e) {
            getLogger().error("{}:{} Failed to write property, reason: {}", dbusObjectPath, "Alias", e.getMessage());
            throw new BluezException("Failed to block device " + dbusObjectPath, e);
        }   
        // do not update cache just yet
    }   

    @Override
    public void setBlocked(boolean blocked) throws BluezException {
        // call remote part
        if (allowRemoteCalls) {
            setBlockedRemote(blocked);
        }
        // local part - NOP
    }  

    private void isServicesResolvedRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Boolean>attemptCachedPropertyUpdate("ServicesResolved");
    }   

    @Override
    public boolean isServicesResolved() {
        // call remote part
        if (allowRemoteCalls) {
            isServicesResolvedRemote();
        }
        // local part
        return this.cache.<Boolean>get("ServicesResolved").booleanValue();
    }  

    @Override
    public boolean isBleEnabled() {
        // local part hides call to remote
        return getBluetoothClass() == 0;
    }  

    /* service/manufacturer */

    private <K> Map<String, String> hexdump(Map<K, byte[]> raw) {
        return raw.entrySet().stream().collect(Collectors.toMap(
            entry -> entry.getKey().toString(),
            entry -> DataConversionUtils.convert(entry.getValue(), 16)
        ));
    }

    private void getServiceDataRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Map<String, Variant>>attemptCachedPropertyUpdate("ServiceData");
    }

    private Map<String, byte[]> convertServiceData(Map<String, Variant> data) {
        if (data == null) {
            return Collections.emptyMap();
        }

        return data.entrySet().stream().collect(Collectors.toMap(
            entry -> entry.getKey(),
            entry -> (byte[])(entry.getValue().getValue())
        ));
    }

    @Override
    public Map<String, byte[]> getServiceData() throws BluezException {
        // call remote part
        getServiceDataRemote();

        // and local part
        return convertServiceData(this.cache.<Map<String, Variant>>get("ServiceData"));
    }

    private void getManufacturerDataRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Map<Short, Variant>>attemptCachedPropertyUpdate("ManufacturerData");
    }

    private Map<Short, byte[]> convertManufacturerData(Map<UInt16, Variant> data) {
        if (data == null) {
            return Collections.emptyMap();
        }

        return data.entrySet().stream().collect(Collectors.toMap(
            entry -> entry.getKey().shortValue(),
            entry -> (byte[])(entry.getValue().getValue())
        ));
    }

    @Override
    public Map<Short, byte[]> getManufacturerData() throws BluezException {
        // call remote part
        getManufacturerDataRemote();

        // and local part
        return convertManufacturerData(this.cache.<Map<UInt16, Variant>>get("ManufacturerData"));
    }
}
