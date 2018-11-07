package cz.organovabanka.bluetooth.manager.transport.dbus.wrappers;

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
import cz.organovabanka.bluetooth.manager.transport.dbus.wrappers.WrappedBluezService;

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
public class WrappedBluezDevice implements BluezDevice {
    private BluezDevice delegate;
    public BluezDevice getDelegate() {
        return delegate;
    }
    public WrappedBluezDevice(BluezDevice deleg) {
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

    public boolean disconnect() {
        try {
            return delegate.disconnect();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on disconnect {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean connect() {
        try {
            return delegate.connect();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on connect {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void enableBlockedNotifications(Notification<Boolean> notification) {
        try {
            delegate.enableBlockedNotifications(notification);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on enableBlockedNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void disableBlockedNotifications() {
        try {
            delegate.disableBlockedNotifications();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on disableBlockedNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void enableRSSINotifications(Notification<Short> notification) {
        try {
            delegate.enableRSSINotifications(notification);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on enableRSSINotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void disableRSSINotifications() {
        try {
            delegate.disableRSSINotifications();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on disableRSSINotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void enableConnectedNotifications(Notification<Boolean> notification) {
        try {
            delegate.enableConnectedNotifications(notification);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on enableConnectedNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void disableConnectedNotifications() {
        try {
            delegate.disableConnectedNotifications();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on disableConnectedNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void enableServicesResolvedNotifications(Notification<Boolean> notification) {
        try {
            delegate.enableServicesResolvedNotifications(notification);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on enableServicesResolvedNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void disableServicesResolvedNotifications() {
        try {
            delegate.disableServicesResolvedNotifications();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on disableServicesResolvedNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void enableServiceDataNotifications(Notification<Map<String, byte[]>> notification) {
        try {
            delegate.enableServiceDataNotifications( notification);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on enableServiceDataNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void disableServiceDataNotifications() {
        try {
            delegate.disableServiceDataNotifications();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on disableServiceDataNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void enableManufacturerDataNotifications(Notification<Map<Short, byte[]>> notification) {
        try {
            delegate.enableManufacturerDataNotifications( notification);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on enableManufacturerDataNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void disableManufacturerDataNotifications() {
        try {
            delegate.disableManufacturerDataNotifications();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on disableManufacturerDataNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public List<Service> getServices() {
        try {
            return delegate.getServices().stream().map((s) -> ((s instanceof BluezService) ? new WrappedBluezService((BluezService)s) : s)).collect(Collectors.toList());
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getServices {}:{}", e.getClass().getName(), getURL(), e.getMessage());
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

    public BluetoothAddressType getAddressType() {
        try {
            return delegate.getAddressType();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getAddressType {}:{}", e.getClass().getName(), getURL(), e.getMessage());
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

    public short getTxPower() {
        try {
            return delegate.getTxPower();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getTxPower {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public short getRSSI() {
        try {
            return delegate.getRSSI();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getRSSI {}:{}", e.getClass().getName(), getURL(), e.getMessage());
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

    public boolean isConnected() {
        try {
            return delegate.isConnected();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isConnected {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isTrusted() {
        try {
            return delegate.isTrusted();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isTrusted {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isPaired() {
        try {
            return delegate.isPaired();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isPaired {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isBlocked() {
        try {
            return delegate.isBlocked();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isBlocked {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void setBlocked(boolean blocked) {
        try {
            delegate.setBlocked(blocked);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on setBlocked {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isServicesResolved() {
        try {
            return delegate.isServicesResolved();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isServicesResolved {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isBleEnabled() {
        try {
            return delegate.isBleEnabled();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isBleEnabled {}:{}", e.getClass().getName(), getURL(), e.getMessage());
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
    public Map<Short, byte[]> getManufacturerData() {
        try {
            return delegate.getManufacturerData();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getManufacturedData {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    public Map<String, byte[]> getServiceData() {
        try {
            return delegate.getServiceData();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getServiceData {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}





