package cz.organovabanka.bluetooth.manager.transport.dbus.interfaces;

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
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusInterfaceName;
import org.freedesktop.dbus.DBusMemberName;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interface mirroring DBus properties interface methods.
 * Inner interfaces and classes are used for signal handling
 * @author Lukas Rucka
 */
@DBusInterfaceName("org.freedesktop.DBus.Properties")
public interface Properties extends DBus.Properties {
    public interface Cache {
        public <T> void update(String name, T value);

        public void update(Map<String, Variant> values);

        public <T> T get(String name);
    }

    @DBusMemberName("PropertiesChanged")
    public static class PropertiesChanged extends DBusSignal {
        private final String iface;
        private final Map<String, Variant> propertiesChanged;
        private final List<String> propertiesRemoved;

        public PropertiesChanged(
            String path, 
            String iface, 
            Map<String, Variant> propertiesChanged, 
            List<String> propertiesRemoved
        ) throws DBusException {
            super(path, iface, propertiesChanged, propertiesRemoved);
            this.iface = iface;
            this.propertiesChanged = propertiesChanged;
            this.propertiesRemoved = propertiesRemoved;
        }

        public String getIface() {
            return iface;
        }

        public Map<String, Variant> getPropertiesChanged() {
            return propertiesChanged;
        }

        public List<String> getPropertiesRemoved() {
            return propertiesRemoved;
        }
    }
}

