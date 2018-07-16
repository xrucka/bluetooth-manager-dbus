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

import cz.organovabanka.bluetooth.manager.transport.dbus.impl.NativeBluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.impl.NativeBluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.impl.NativeBluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Adapter1;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;

/**
 * A class representing Bluez adapters.
 * @author Lukas Rucka
 */
public abstract class AbstractBluezAdapter extends BluezObjectBase implements Adapter {
    protected Notification<Boolean> notificationDiscovering = null;
    protected Notification<Boolean> notificationPowered = null;

    protected AbstractBluezAdapter(BluezContext context, String dbusObjectPath, String primaryInterface) throws BluezException {
        super(context, dbusObjectPath, primaryInterface);

        cache.set("Powered", new Boolean(false));
        cache.set("Discovering", new Boolean(false));
        cache.set("Discoverable", new Boolean(false));

        cache.set("Address", "XX:XX:XX:XX:XX:XX");

        cache.set("Alias", "Unknown");
        cache.set("Name", "Unknown");

        cache.set("Class", new UInt32(0));

        cache.set("url", BluezCommons.DBUSB_PROTOCOL_NAME + "://XX:XX:XX:XX:XX:XX/YY:YY:YY:YY:YY:YY");
    }

    protected abstract Logger getLogger();

    /* dbus & openhab handles */

    /* begin remote adapter methods */

    @Override
    public abstract boolean stopDiscovery() throws BluezException;

    @Override
    public abstract boolean startDiscovery() throws BluezException;

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
    public abstract List<Device> getDevices() throws BluezException;

    @Override
    protected void disposeLocal(boolean doRemoteCalls, boolean recurse) {
        // local part

        // first disable notifications
        disableDiscoveringNotifications();
        disablePoweredNotifications();
        // todo: consider recursive on devices
    }

    @Override
    public void dispose(boolean doRemoteCalls, boolean recurse) { 
        getLogger().debug("{}:{} Disposing characteristic", dbusObjectPath, getURL().getAdapterAddress());
        super.dispose(doRemoteCalls, recurse);
    }

    /* access attributes */

    public String getAddress() {
       return cache.<String>get("Address");
    }  

    @Override
    public String getAlias() {
        return cache.<String>get("Alias");
    }  

    @Override
    public abstract void setAlias(String alias) throws BluezException;

    @Override
    public String getName() {
        return cache.<String>get("Name");
    }  

    /* link quality attributes */

    //@Override
    public int getBluetoothClass() {
        return cache.<UInt32>get("Class").intValue();
    }  

    /* discovery related attributes */

    @Override
    public boolean isDiscovering() {
        return cache.<Boolean>get("Discovering").booleanValue();
    }  

    @Override
    public boolean isPowered() {
        return cache.<Boolean>get("Powered").booleanValue();
    }  

    @Override
    public abstract void setPowered(boolean powered) throws BluezException;

    //@Override
    public boolean isDiscoverable() {
        return cache.<Boolean>get("Discoverable").booleanValue();
    }  

    //@Override
    public abstract void setDiscoverable(boolean powered) throws BluezException;

    public abstract void removeDevice(String path);
}
