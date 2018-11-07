package cz.organovabanka.bluetooth.manager.transport.dbus.wrappers;

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
import org.freedesktop.dbus.interfaces.ObjectManager;
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
public class WrappedBluezAdapter implements BluezAdapter {
    private BluezAdapter delegate;

    public BluezAdapter getDelegate() {
        return delegate;
    }

    public WrappedBluezAdapter(BluezAdapter deleg) {
        delegate = deleg;
    }

    public Logger getLogger() {
        return delegate.getLogger();
    }

    public URL getURL() {
        try {
            return delegate.getURL();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getURL {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean stopDiscovery() {
        try {
            return delegate.stopDiscovery();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on stopDiscovery {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean startDiscovery() {
        try {
            return delegate.startDiscovery();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on startDiscovery {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void enablePoweredNotifications(Notification<Boolean> notification) {
        try {
            delegate.enablePoweredNotifications(notification);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on enablePoweredNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void disablePoweredNotifications() {
        try {
            delegate.disablePoweredNotifications();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on disablePoweredNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void enableDiscoveringNotifications(Notification<Boolean> notification) {
        try {
            delegate.enableDiscoveringNotifications(notification);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on enableDiscoveringNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void disableDiscoveringNotifications() {
        try {
            delegate.disableDiscoveringNotifications();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on disableDiscoveringNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public List<Device> getDevices() {
        try {
            return delegate.getDevices();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getDevices {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void dispose() {
        try {
            delegate.dispose();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on dispose {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public String getAddress() {
        try {
            return delegate.getAddress();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getAddress {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public String getAlias() {
        try {
            return delegate.getAlias();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getAlias {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void setAlias(String alias) {
        try {
            delegate.setAlias(alias);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on setAlias {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public String getName() {
        try {
            return delegate.getName();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getName {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public int getBluetoothClass() {
        try {
            return delegate.getBluetoothClass();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getBluetoothClass {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isDiscovering() {
        try {
            return delegate.isDiscovering();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isDiscovering {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isPowered() {
        try {
            return delegate.isPowered();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isPowered {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void setPowered(boolean powered) {
        try {
            delegate.setPowered(powered);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on setPowered {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isDiscoverable() {
        try {
            return delegate.isDiscoverable();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isDiscoverable {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void setDiscoverable(boolean discoverable) {
        try {
            delegate.setDiscoverable(discoverable);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on setDiscoverable {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isActive() {
        try {
            return delegate.isActive();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isActive {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    public void activate() {
        try {
            delegate.activate();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on activate {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}



