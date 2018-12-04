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
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.GattCharacteristic1;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezObject;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezService;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.DataConversionUtils;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class representing gatt device characteristics (dbus implementation).
 * @author Lukas Rucka
 */
public class WrappedBluezCharacteristic implements BluezCharacteristic {

    private BluezCharacteristic delegate;

    public BluezCharacteristic getDelegate() {
        return delegate;
    }  

    public WrappedBluezCharacteristic(BluezCharacteristic deleg) {
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

    public void dispose() {
        try {
            delegate.dispose();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on dispose {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public Set<CharacteristicAccessType> getFlags() {
        try {
            return delegate.getFlags();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getFlags {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isNotifying() {
        try {
            return delegate.isNotifying();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isNotifying {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public byte[] readValue() {
        try {
            return delegate.readValue();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on readValue {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void enableValueNotifications(Notification<byte[]> notification) {
        try {
            delegate.enableValueNotifications(notification);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on enableValueNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void disableValueNotifications() {
        try {
            delegate.disableValueNotifications();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on disableValueNotifications {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean writeValue(byte[] bytes) {
        try {
            return delegate.writeValue(bytes);
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on writeValue {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isNotificationConfigurable() {
        try {
            return delegate.isNotificationConfigurable();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on isNotificationConfigurable {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public String getUUID() {
        try {
            return delegate.getUUID();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getUUID {}:{}", e.getClass().getName(), getURL(), e.getMessage());
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



