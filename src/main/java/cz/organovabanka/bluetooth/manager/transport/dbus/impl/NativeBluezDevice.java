package cz.organovabanka.bluetooth.manager.transport.dbus.impl;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezService;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezContext;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezException;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezFactory;
import cz.organovabanka.bluetooth.manager.transport.dbus.impl.NativeBluezService;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Device1;
import cz.organovabanka.bluetooth.manager.transport.dbus.virtualized.Battery1;
import cz.organovabanka.bluetooth.manager.transport.dbus.virtualized.BatteryService;

/**
 * A class representing Bluez devices.
 * @author Lukas Rucka
 */
public class NativeBluezDevice extends AbstractBluezDevice {
    private static final Logger logger = LoggerFactory.getLogger(NativeBluezDevice.class);

    private final Device1 remoteInterface;

    public NativeBluezDevice(BluezContext context, String dbusObjectPath) throws BluezException {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_DEVICE);

        // setup default values of cached attributes
        // todo following
        try {
            this.remoteInterface = busConnection.getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Device1.class);
        } catch (DBusException e) {
            throw new BluezException("Unable to access dbus objects for " + dbusObjectPath, e); 
        }

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

    @Override
    protected Logger getLogger() {
        return logger;
    }

    /* dbus & openhab handles */

    @Override
    protected void updateURL() throws BluezException {
        try {
            String adapterPath = getAdapterPath();
            AbstractBluezAdapter adapter = context.getManagedAdapter(adapterPath, false);
            URL url = adapter.getURL().copyWithDevice(getAddress());
            cache.set("url", url.toString());
        } catch (BluezException e) {
            getLogger().error("{}: Unable to update URL, reason: {}", dbusObjectPath, e.getMessage());
        }
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
	        return Collections.unmodifiableList(
                allObjects.entrySet().stream()
                .map((entry) -> { return entry.getKey().toString(); })
                .filter((path) -> { return servicePattern.matcher(path).matches(); })
                .map((path) -> { return new NativeBluezService(context, path); })
                .collect(Collectors.toList())
            );
        } catch (RuntimeException e) {
            throw new BluezException("Unable to unpack bluez objects when processing " + dbusObjectPath, e);
        }
    }

    @Override
    protected void disposeRemote() {
        // remote part
        if (!allowRemoteCalls) {
            return;
        }

        disconnectRemote();
    }

    /* access attributes */

    private void getAddressTypeRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<String>attemptCachedPropertyUpdate("AddressType");
    }

    @Override
    public BluetoothAddressType getAddressType() {
        // call remote part
        getAddressTypeRemote();
        // local part
        return super.getAddressType();
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
        return super.getAddress();
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
        return super.getAlias();
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
        return super.getName();
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
        return super.getTxPower();
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
        return super.getRSSI();
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
        return super.getBluetoothClass();
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
        return super.isConnected();
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
        return super.isTrusted();
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
        return super.isPaired();
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
        return super.isPaired();
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
        return super.isServicesResolved();
    }  

    /* service/manufacturer */

    private void getServiceDataRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Map<String, Variant>>attemptCachedPropertyUpdate("ServiceData");
    }

    @Override
    public Map<String, byte[]> getServiceData() throws BluezException {
        // call remote part
        getServiceDataRemote();

        // and local part
        return super.getServiceData();
    }

    private void getManufacturerDataRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Map<Short, Variant>>attemptCachedPropertyUpdate("ManufacturerData");
    }

    @Override
    public Map<Short, byte[]> getManufacturerData() throws BluezException {
        // call remote part
        getManufacturerDataRemote();

        // and local part
        return super.getManufacturerData();
    }
}
