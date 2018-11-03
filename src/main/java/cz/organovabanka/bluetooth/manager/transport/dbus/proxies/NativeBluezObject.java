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

import org.freedesktop.DBus;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.exceptions.NotConnected;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.DataConversionUtils;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A common component for all proxies Bluez objects.
 * @author Lukas Rucka
 */
public abstract class NativeBluezObject {
    // every method has remote + local part
    // order - first do remote, then local part

    protected final BluezContext context;

    protected final String dbusObjectPath;
    protected final String primaryInterface;
    protected final Properties objectProperties;

    protected final Map<String, Consumer<Variant<?>>> handlers = new HashMap<String, Consumer<Variant<?>>>();

    protected boolean active = false;

    protected NativeBluezObject(BluezContext context, String dbusObjectPath, String primaryInterface) throws BluezException {
        this.context = context;
        this.dbusObjectPath = dbusObjectPath;
        this.primaryInterface = primaryInterface;

        try {
            this.objectProperties = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Properties.class);
        } catch (DBusException e) {
            throw new BluezException("Unable to access properties of " + dbusObjectPath + ": " + e.getMessage(), e);
        }

        activate();
    }

    public void commitNotifications(Map<String, Variant<?>> changed) {
        if (getLogger().isDebugEnabled()) {
            String props = changed.keySet().stream().collect(Collectors.joining(", "));
            getLogger().debug("Got updated properties of {} ({}) : {}", getPath(), getURL(), props);
        }
        changed.entrySet().stream().forEach((entry) -> {
            Consumer<Variant<?>> handler = handlers.get(entry.getKey());
            if (handler == null) {
                return;
            }

            handler.accept(entry.getValue());
        });
    }

    public abstract URL getURL();

    protected abstract Logger getLogger();

    protected <T> T readProperty(String property) {
        getLogger().trace("Reading property {}:{} of {} ({})", primaryInterface, property, getPath(), getURL());
        return (T)objectProperties.Get(primaryInterface, property);
    }

    protected <T> T readOptionalProperty(String property, Supplier<T> fallbackAction) {
        try {
            return readProperty(property);
        } catch (DBusExecutionException ex) {
            // perhaps checking for org.freedesktop.DBus.Error.InvalidArgs in getType() would be better
            if (ex.getMessage().contains("No such property")) {
                // do fallback
            } else {
                throw ex;
            }
        }

        return fallbackAction.get();
    }

    protected <T> void writeProperty(String iface, String property, T value) throws NotReadyException {
        getLogger().trace("Writing property {}:{}={} of {} ({})", iface, property, value.toString(), getPath(), getURL());
        try {
            Properties properties = objectProperties;
            if (!primaryInterface.equals(iface)) {
                properties = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Properties.class);
            }
            properties.Set(iface, property, value);
        } catch (DBusException ex) {
            throw new NotReadyException(ex.toString());
        }
    }

    public String getPath() {
        // local part only
        return dbusObjectPath;
    }

    public String getDBusIfaceName() {
        return primaryInterface;
    }

    public void activate() {
        active = true;
    }

    public boolean isActive() {
        return active;
    }

    public void dispose() {
        active = false;
    }

    protected <T> T callWithCleanup(Callable<T> call, Runnable disposer) throws NotReadyException {
        try {
            return call.call();
        } catch (NotConnected cause) {
            throw new NotReadyException("Device is not connected " + getPath());
        } catch (DBusExecutionException cause) {
            if (cause.getMessage().matches("^.*Method \".*\" with signature \".*\" on interface .*$")) {
                disposer.run();
            } else if (cause.getMessage().matches("^.*[Nn]ot connected.*$")) {
                throw new NotReadyException("Device is not connected " + getPath());
            } else {
                throw new NotReadyException(cause.toString());
            }
        } catch (RuntimeException cause) {
            if (cause.getMessage().matches("^.*Method \".*\" with signature \".*\" on interface .*$")) {
                disposer.run();
            } else if (cause.getMessage().matches("^.*[Nn]ot connected.*$")) {
                throw new NotReadyException("Device is not connected " + getPath());
            } else {
                throw cause;
            }
        } catch (Exception cause) {
            throw new NotReadyException("Generic failure on " + getPath());
        }
        return null;
    }

    protected void callWithCleanup(Runnable call, Runnable disposer) throws NotReadyException {
        try {
            call.run();
        } catch (NotConnected cause) {
            throw new NotReadyException("Device is not connected " + getPath());
        } catch (DBusExecutionException cause) {
            if (cause.getMessage().matches("^.*Method \".*\" with signature \".*\" on interface .*$")) {
                disposer.run();
            } else if (cause.getMessage().matches("^.*[Nn]ot connected.*$")) {
                throw new NotReadyException("Device is not connected " + getPath());
            } else {
                throw new NotReadyException(cause.toString());
            }
        } catch (RuntimeException cause) {
            if (cause.getMessage().matches("^.*Method \".*\" with signature \".*\" on interface .*$")) {
                disposer.run();
            } else if (cause.getMessage().matches("^.*[Nn]ot connected.*$")) {
                throw new NotReadyException("Device is not connected " + getPath());
            } else {
                throw new NotReadyException(cause.toString());
            }
        }
    }
}
