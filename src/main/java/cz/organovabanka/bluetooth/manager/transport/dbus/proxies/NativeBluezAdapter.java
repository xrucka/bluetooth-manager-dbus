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
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezFactory;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezHooks;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Adapter1;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezObject;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Device;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A class representing Bluez adapters.
 * @author Lukas Rucka
 */
public class NativeBluezAdapter extends NativeBluezObject implements BluezAdapter {

    private static final Logger logger = LoggerFactory.getLogger(NativeBluezAdapter.class);

    private final Adapter1 remoteInterface;
    private final URL objectURL;

    private Notification<Boolean> notificationDiscovering = null;
    private Notification<Boolean> notificationPowered = null;

    public NativeBluezAdapter(BluezContext context, String dbusObjectPath) throws BluezException {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_ADAPTER);

        try {
            this.remoteInterface = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Adapter1.class);
        } catch (DBusException e) {
            throw new BluezException("Unable to access dbus objects for " + dbusObjectPath, e); 
        }

        objectURL = makeURL();
        setupHandlers();

        logger.info("Created bluez adapter proxy for {}", dbusObjectPath);
    }

    private void setupHandlers() {
        this.handlers.put("Powered", (powered) -> {
            if (notificationPowered == null) {
                return;
            }

            context.notifySafely(() -> {
                notificationPowered.notify( (Boolean)(powered.getValue()) );
            }, logger, dbusObjectPath + ":Powered");
        });
        this.handlers.put("Discovering", (discovering) -> {
            if (notificationDiscovering == null) {
                return;
            }

            context.notifySafely(() -> {
                notificationDiscovering.notify((Boolean)discovering.getValue());
            }, logger, dbusObjectPath + ":Discovering");
        });
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    private URL makeURL() {
        return new URL(BluezCommons.DBUSB_PROTOCOL_NAME + "://" + getAddress());
    }

   
    @Override
    public URL getURL() {
        return objectURL;
    }

    /* begin remote adapter methods */

    @Override
    public boolean stopDiscovery() {
        getLogger().trace("Invoking {}() of {} ({})", "stopDiscovery", getPath(), getURL());

        callWithCleanup(
            () -> { 
                try {
                    remoteInterface.StopDiscovery();
                } catch (DBusExecutionException e) {
                    if (e.getMessage().contains("No discovery started")) {
                        // nop, silently ignore
                    } else {
                        throw e;
                    }
                }
            },
            () -> { context.dropAdapter(getURL()); }
        );
        return true;
    }   

    @Override
    public boolean startDiscovery() {
        Map<String, Variant> filterOptions = new HashMap<String, Variant>();
        filterOptions.put("RSSI", new Variant<Short>(new Short((short)-100), "n"));
        getLogger().trace("Invoking {}() of {} ({})", "startDiscovery", getPath(), getURL());

        callWithCleanup(
            () -> { remoteInterface.SetDiscoveryFilter(filterOptions); },
            () -> { context.dropAdapter(getURL()); }
        );

        callWithCleanup(
            () -> {
                try {
                    remoteInterface.StartDiscovery();
                } catch (DBusExecutionException e) {
                    if (e.getMessage().contains("Operation already in progress")) {
                        // nop, silently ignore
                    } else {
                        throw e;
                    }
                }
            },
            () -> { context.dropAdapter(getURL()); }
        );

        return isDiscovering();
    }  
 
    public void removeDevice(String devicePath) {
        getLogger().trace("Invoking {}() of {} ({})", "removeDevice", getPath(), getURL());
        remoteInterface.RemoveDevice(new DBusPath(devicePath));
    }

    /* notification setters */

    @Override
    public void enablePoweredNotifications(Notification<Boolean> notification) {
        notificationPowered = notification;
    }

    @Override
    public void disablePoweredNotifications() {
        notificationPowered = null;
    }

    @Override
    public void enableDiscoveringNotifications(Notification<Boolean> notification) {
        notificationDiscovering = notification;
    }

    @Override
    public void disableDiscoveringNotifications() {
        notificationDiscovering = null;
    }

    /* subtree list */

    @Override
    public List<Device> getDevices() {
        List<BluezDevice> discoveredDevices = new ArrayList<>();
        for (BluezHooks.PostDeviceDiscoveryHook hook : context.getHooks().getPostDeviceDiscoveryHooks()) {
            hook.trigger(context, this, discoveredDevices);
            //BluezCommons.runSafely(() -> { hook.trigger(context, this, discoveredDevices); });
        }

        return discoveredDevices.stream().collect(Collectors.toList());
    }

    @Override
    public void dispose() {
        // tinyb stopne discovery, disposne rekurzivne vsechna zarizeni pod adapterem a nakonec zrusi notifikace

        this.disableDiscoveringNotifications();
        this.disablePoweredNotifications();

        super.dispose();
    }

    /* access attributes */

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
        return this.<String>readProperty("Name");
    }  

    /* link quality attributes */

    @Override
    public int getBluetoothClass() {
        return this.<UInt32>readProperty("Class").intValue();
    }  

    /* discovery related attributes */

    @Override
    public boolean isDiscovering() {
        return this.<Boolean>readProperty("Discovering").booleanValue();
    }  

    @Override
    public boolean isPowered() {
        return this.<Boolean>readProperty("Powered").booleanValue();
    }  

    @Override
    public void setPowered(boolean powered) {
        // local is a bit complicated
        try {
            // bluetooth manager calls power down to "reset" angry devices
            if (powered) {
                Thread.sleep(500);
            }
        } catch (InterruptedException e) { 
            /* silently ignore */
        }

        this.<Boolean>writeProperty(primaryInterface, "Powered", powered);

        try {
            if (!powered) {
                Thread.sleep(500);
            }
        } catch (InterruptedException e) { 
            /* silently ignore */
        }
    }  

    @Override
    public boolean isDiscoverable() {
        return this.<Boolean>readProperty("Discoverable").booleanValue();
    }  

    @Override
    public void setDiscoverable(boolean discoverable) {
        this.<Boolean>writeProperty(primaryInterface, "Discoverable", discoverable);
    }   
}
