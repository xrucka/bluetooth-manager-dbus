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
import org.freedesktop.DBus.Properties;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusInterfaceName;
import org.freedesktop.dbus.DBusMemberName;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.exceptions.DBusException;

import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interface mirroring DBus object manager interface methods, providing way to probe Bluez objects.
 * @author Lukas Rucka
 */
@DBusInterfaceName("org.freedesktop.DBus.ObjectManager")
public interface ObjectManager extends DBusInterface {
    Map<Path, Map<String, Map<String, Variant>>> GetManagedObjects();

    @DBusMemberName("InterfacesAdded")
    public static class InterfacesAdded extends DBusSignal {
        private final Path objectPath;
        private final Map<String, Map<String, Variant>> interfacesAdded;

        public InterfacesAdded(String path, Path objectPath, Map<String, Map<String, Variant>> interfacesAdded) throws DBusException {
            super(path, objectPath, interfacesAdded);
            this.objectPath = objectPath;
            this.interfacesAdded = interfacesAdded;
        }

        public Path getObjectPath() {
            return objectPath;
        }

        public Map<String, Map<String, Variant>> getInterfacesAdded() {
            return interfacesAdded;
        }
    }

    @DBusMemberName("InterfacesRemoved")
    public static class InterfacesRemoved extends DBusSignal {
        private final Path objectPath;
        private final List<String> interfacesRemoved;

        public InterfacesRemoved(String path, Path objectPath, List<String> interfacesRemoved) throws DBusException {
            super(path, objectPath, interfacesRemoved);
            this.objectPath = objectPath;
            this.interfacesRemoved = interfacesRemoved;
        }

        public Path getObjectPath() {
            return objectPath;
        }

        public List<String> getInterfacesRemoved() {
            return interfacesRemoved;
        }
    }
}
