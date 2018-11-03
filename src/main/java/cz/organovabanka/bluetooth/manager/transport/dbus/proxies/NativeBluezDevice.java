package cz.organovabanka.bluetooth.manager.transport.dbus.proxies;

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

import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezContext;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezException;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezFactory;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezHooks;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Device1;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezObject;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezService;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.DataConversionUtils;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothAddressType;
import org.sputnikdev.bluetooth.manager.transport.Notification;
import org.sputnikdev.bluetooth.manager.transport.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A class representing Bluez devices.
 * @author Lukas Rucka
 */
public class NativeBluezDevice extends NativeBluezObject implements BluezDevice {
    private static final Logger logger = LoggerFactory.getLogger(NativeBluezDevice.class);

    private final Device1 remoteInterface;
    private final URL objectURL;

    private Notification<Short> notificationRssi = null;
    private Notification<Boolean> notificationBlocked = null;
    private Notification<Boolean> notificationConnected = null;
    private Notification<Boolean> notificationServicesResolved = null;
    private Notification<Map<String, byte[]>> notificationServiceData = null;
    private Notification<Map<Short, byte[]>> notificationManufacturerData = null;

    public NativeBluezDevice(BluezContext context, String dbusObjectPath, URL parentAdapterURL) throws BluezException {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_DEVICE);

        try {
            this.remoteInterface = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Device1.class);
        } catch (DBusException e) {
            throw new BluezException("Unable to access dbus objects for " + dbusObjectPath, e); 
        }

        objectURL = makeURL(parentAdapterURL);
        setupHandlers();
    }

    private void setupHandlers() {
        this.handlers.put("RSSI", (rssi) -> {
            if (notificationRssi == null) {
                return;
            }

            context.notifySafely(
                () -> { notificationRssi.notify(((Short)rssi.getValue()).shortValue()); }, 
                getLogger(), dbusObjectPath + ":RSSI");
        });

        this.handlers.put("Blocked", (blocked) -> {
            if (notificationBlocked == null) {
                return;
            }

            context.notifySafely(
                () -> { notificationBlocked.notify(((Boolean)blocked.getValue()).booleanValue()); }, 
                getLogger(), dbusObjectPath + ":Blocked"); 
        });
        this.handlers.put("Connected", (connected) -> {
            if (notificationConnected == null) {
                return;
            }

            boolean value = ((Boolean)connected.getValue()).booleanValue();

            context.notifySafely(
                () -> { notificationConnected.notify(value); }, 
                getLogger(), dbusObjectPath + ":Connected");
        });
        this.handlers.put("ServicesResolved", (resolved) -> {
            if (notificationServicesResolved == null) {
                return;
            }

            boolean value = (Boolean)resolved.getValue();

            context.notifySafely(
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

            context.notifySafely(
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

            context.notifySafely(
                () -> { notificationManufacturerData.notify(rawData); }, 
                getLogger(), dbusObjectPath + ":ManufacturerData");

        });
    } 

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public URL getURL() {
        return objectURL;
    }

    /* dbus & openhab handles */

    protected URL makeURL(URL parentAdapterURL) {
        return parentAdapterURL.copyWithDevice(getAddress());
    }

    public String getAdapterPath() {
        // local part only
        return BluezCommons.parsePath(dbusObjectPath, BluezAdapter.class);
    }

    /* begin remote device methods */

    @Override
    public boolean disconnect() {
        getLogger().trace("Invoking {}() of {} ({})", "disconnect", getPath(), getURL());
        callWithCleanup(
            () -> { remoteInterface.Disconnect(); },
            () -> { context.dropDevice(getURL()); }
        );
        // check whether cleanup fired
        return context.getManagedDevice(getURL()) != null;
    }   

    @Override
    public boolean connect() {
        getLogger().trace("Invoking {}() of {} ({})", "connect", getPath(), getURL());
        callWithCleanup(
            () -> { remoteInterface.Connect(); },
            () -> { context.dropDevice(getURL()); }
        );
        // check whether cleanup fired
        return context.getManagedDevice(getURL()) != null;
    }  
 
    /* notification setters */

    @Override
    public void enableBlockedNotifications(Notification<Boolean> notification) {
        notificationBlocked = notification;
    }

    @Override
    public void disableBlockedNotifications() {
        notificationBlocked = null;
    }

    @Override
    public void enableRSSINotifications(Notification<Short> notification) {
        notificationRssi = notification;
    }

    @Override
    public void disableRSSINotifications() {
        notificationRssi = null;
    }

    @Override
    public void enableConnectedNotifications(Notification<Boolean> notification) {
        getLogger().debug("Explicitly enabling connected notifications for {} ({})", getPath(), getURL());
        notificationConnected = notification;
    }

    @Override
    public void disableConnectedNotifications() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        getLogger().debug("Explicitly disabling connected notifications for {} ({}): {}  by {} by {}", getPath(), getURL(), st[1], st[2], st[3]);
        notificationConnected = null;
    }

    @Override
    public void enableServicesResolvedNotifications(Notification<Boolean> notification) {
        notificationServicesResolved = notification;
    }

    @Override
    public void disableServicesResolvedNotifications() {
        notificationServicesResolved = null;
    }

    @Override
    public void enableServiceDataNotifications(Notification<Map<String, byte[]>> notification) {
        notificationServiceData = notification;
    }

    @Override
    public void disableServiceDataNotifications() {
        notificationServiceData = null;
    }

    @Override
    public void enableManufacturerDataNotifications(Notification<Map<Short, byte[]>> notification) {
        notificationManufacturerData = notification;
    }

    @Override
    public void disableManufacturerDataNotifications() {
        notificationManufacturerData = null;
    }

    /* subtree list */

    @Override
    public List<Service> getServices() {
        List<BluezService> discoveredServices = new ArrayList<>();
        for (BluezHooks.PostServiceDiscoveryHook hook : context.getHooks().getPostServiceDiscoveryHooks()) {
            hook.trigger(this, discoveredServices, context);
        }

        return discoveredServices.stream().collect(Collectors.toList());
    }

    @Override
    public void dispose() {
        disconnect();

        disableBlockedNotifications();
        disableConnectedNotifications();
        disableRSSINotifications();
        disableServicesResolvedNotifications();
        disableManufacturerDataNotifications();
        disableServiceDataNotifications();

        super.dispose();
    }

    /* access attributes */

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
        String addressType = this.<String>readProperty("AddressType");
        return parseAddressType(addressType);
    }

    @Override
    public String getAddress() {
        return this.<String>readProperty("Address");
    }  

    @Override
    public String getAlias() {
        return this.<String>readProperty("Alias");
    }  

    @Override
    public void setAlias(String alias) {
        this.<String>writeProperty(primaryInterface, "Alias", alias);
    }   

    @Override
    public String getName() {
        // Name is optional, if fails, use Alias instead
        return this.<String>readOptionalProperty("Name", () -> this.getAlias());
    }  

    /* link quality attributes */

    @Override
    public short getTxPower() {
        // TxPower is optional, if fails, default to -99
        return this.<Short>readOptionalProperty("TxPower", () -> new Short((short)-99)).shortValue();
    }  

    @Override
    public short getRSSI() {
        // RSSI is optional, if fails, use TxPower instead
        return this.<Short>readOptionalProperty("RSSI", () -> new Short(this.getTxPower())).shortValue();
    }  

    @Override
    public int getBluetoothClass() {
        // Class is optional, if fails, default to 0 instead
        return this.<UInt32>readOptionalProperty("Class", () -> new UInt32(0)).intValue();
    }  

    /* connection related attributes */

    @Override
    public boolean isConnected() {
        return this.<Boolean>readProperty("Connected");
    }  

    @Override
    public boolean isTrusted() {
        return this.<Boolean>readProperty("Trusted").booleanValue();
    }  

    @Override
    public boolean isPaired() {
        return this.<Boolean>readProperty("Paired").booleanValue();
    }  

    @Override
    public boolean isBlocked() {
        return this.<Boolean>readProperty("Blocked").booleanValue();
    }  

    @Override
    public void setBlocked(boolean blocked) {
        this.<Boolean>writeProperty(primaryInterface, "Blocked", blocked);
    }   

    @Override
    public boolean isServicesResolved() {
        return this.<Boolean>readProperty("ServicesResolved").booleanValue();
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
        // ServiceData is optional, if fails, default to empty map
        Map<String, Variant> data = this.<Map<String, Variant>>readOptionalProperty("ServiceData", () -> Collections.emptyMap());
        return convertServiceData(data);
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
        // ManufacturerData is optional, if fails, default to empty map
        Map<UInt16, Variant> data = this.<Map<UInt16, Variant>>readOptionalProperty("ManufacturerData", () -> Collections.emptyMap());
        return convertManufacturerData(data);
    }
}
