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
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.GattService1;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezObject;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezService;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A class representing Bluez services.
 * @author Lukas Rucka
 */
public class WrappedBluezService implements BluezService {
    private BluezService delegate;
    public BluezService getDelegate() {
        return delegate;
    }
    public WrappedBluezService(BluezService deleg) {
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

    public String getUUID() {
        try {
            return delegate.getUUID();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getUUID {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public List<Characteristic> getCharacteristics() {
        try {
            return delegate.getCharacteristics().stream().map((s) -> ((s instanceof BluezCharacteristic) ? new WrappedBluezCharacteristic((BluezCharacteristic)s) : s)).collect(Collectors.toList());
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on getCharacteristics {}:{}", e.getClass().getName(), getURL(), e.getMessage());
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
    public void dispose() {
        try {
            delegate.dispose();
        } catch (Exception e) {
            delegate.getLogger().error("Got leaked exception {} on dispose {}:{}", e.getClass().getName(), getURL(), e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}



