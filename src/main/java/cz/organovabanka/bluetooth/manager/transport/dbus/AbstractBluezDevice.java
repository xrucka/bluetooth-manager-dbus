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
import org.sputnikdev.bluetooth.manager.transport.Adapter;
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

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Device1;
import cz.organovabanka.bluetooth.manager.transport.dbus.virtualized.Battery1;
import cz.organovabanka.bluetooth.manager.transport.dbus.virtualized.BatteryService;

/**
 * A common class for all bluez devices
 * @author Lukas Rucka
 */
public abstract class AbstractBluezDevice extends BluezObjectBase implements Device {
    protected Notification<Short> notificationRssi = null;
    protected Notification<Boolean> notificationBlocked = null;
    protected Notification<Boolean> notificationConnected = null;
    protected Notification<Boolean> notificationServicesResolved = null;
    protected Notification<Map<String, byte[]>> notificationServiceData = null;
    protected Notification<Map<Short, byte[]>> notificationManufacturerData = null;

    protected AbstractBluezDevice(BluezContext context, String dbusObjectPath, String primaryInterface) throws BluezException {
        super(context, dbusObjectPath, primaryInterface);

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

    public String getAdapterPath() {
        // local part only
        return BluezCommons.parsePath(dbusObjectPath, Adapter.class);
    }

    /* begin remote device methods */

    @Override
    public abstract boolean disconnect() throws BluezException;

    @Override
    public abstract boolean connect() throws BluezException;

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
    public abstract List<Service> getServices() throws BluezException;

    @Override
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

    public void dispose(boolean doRemoteCalls, boolean recurse) {
        getLogger().trace("{}: Disposing device ({}/{})", dbusObjectPath, getURL().getAdapterAddress(), getURL().getDeviceAddress());
        super.dispose(doRemoteCalls, recurse);
    }

    protected BluetoothAddressType parseAddressType(String addressType) {
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
        String addressType = cache.<String>get("AddressType");
        return parseAddressType(addressType);
    }

    //@Override
    public String getAddress() {
        return this.cache.<String>get("Address");
    }  

    @Override
    public String getAlias() {
        return this.cache.<String>get("Alias");
    }  

    @Override
    public abstract void setAlias(String alias) throws BluezException;

    @Override
    public String getName() {
        return this.cache.<String>get("Name");
    }  

    /* link quality attributes */

    @Override
    public short getTxPower() {
        return this.cache.<Short>get("TxPower").shortValue();
    }  

    @Override
    public short getRSSI() {
        return this.cache.<Short>get("RSSI").shortValue();
    }  

    @Override
    public int getBluetoothClass() {
        return this.cache.<UInt32>get("Class").intValue();
    }  

    /* connection related attributes */

    @Override
    public boolean isConnected() {
        return this.cache.<Boolean>get("Connected").booleanValue();
    }  

    //@Override
    public boolean isTrusted() {
        return this.cache.<Boolean>get("Trusted").booleanValue();
    }  

    //@Override
    public boolean isPaired() {
        return this.cache.<Boolean>get("Paired").booleanValue();
    }  

    @Override
    public boolean isBlocked() {
        return this.cache.<Boolean>get("Blocked").booleanValue();
    }  

    @Override
    public abstract void setBlocked(boolean blocked) throws BluezException;

    @Override
    public boolean isServicesResolved() {
        return this.cache.<Boolean>get("ServicesResolved").booleanValue();
    }  

    @Override
    public boolean isBleEnabled() {
        // local part hides call to remote
        return getBluetoothClass() == 0;
    }  

    /* service/manufacturer */

    protected <K> Map<String, String> hexdump(Map<K, byte[]> raw) {
        return raw.entrySet().stream().collect(Collectors.toMap(
            entry -> entry.getKey().toString(),
            entry -> DataConversionUtils.convert(entry.getValue(), 16)
        ));
    }

    protected Map<String, byte[]> convertServiceData(Map<String, Variant> data) {
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
        return convertServiceData(this.cache.<Map<String, Variant>>get("ServiceData"));
    }

    protected Map<Short, byte[]> convertManufacturerData(Map<UInt16, Variant> data) {
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
        return convertManufacturerData(this.cache.<Map<UInt16, Variant>>get("ManufacturerData"));
    }
}
