package cz.organovabanka.bluetooth.manager.transport.dbus;

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
import org.freedesktop.dbus.exceptions.NotConnected;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.DataConversionUtils;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Properties;

import java.lang.Boolean;
import java.lang.Class;
import java.lang.Runnable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A common component for all Bluez objects.
 * @author Lukas Rucka
 */
public abstract class BluezObjectBase {
    // every method has remote + local part
    // order - first do remote, then local part

    protected final BluezContext context;
    protected final DBusConnection busConnection;

    protected final String dbusObjectPath;
    protected final String primaryInterface;
    protected final Properties objectProperties;

    protected final Map<String, Consumer<Variant>> handlers = new HashMap<String, Consumer<Variant>>();

    protected final PropertyCache cache = new PropertyCache();

    protected boolean allowRemoteCalls = true;
    protected int inactive = 0;

    protected BluezObjectBase(BluezContext context, String dbusObjectPath, String primaryInterface) throws BluezException {
        this.context = context;
        this.busConnection = context.getDbusConnection();
        this.dbusObjectPath = dbusObjectPath;
        this.primaryInterface = primaryInterface;

        try {
            synchronized (context.buslock) {
                this.objectProperties = busConnection.getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Properties.class);
            }
        } catch (DBusException e) {
            throw new BluezException("Unable to access properties of " + dbusObjectPath + ": " + e.getMessage(), e);
        }

        activate();
    }

    public Properties.Cache getCache() {
        return cache;
    } 

    public void commitNotifications(Map<String, Variant> changed) {
        changed.entrySet().stream().forEach((entry) -> {
            Consumer<Variant> handler = handlers.get(entry.getKey());
            if (handler == null) {
                return;
            }

            handler.accept(entry.getValue());
        });
    }


    public URL getURL() {
        // this is the local part of getURL
        return new URL(cache.<String>get("url"));
    }

    // this is the remote part of getURL
    protected abstract void updateURL() throws BluezException;

    protected abstract Logger getLogger();

    protected <T> T readProperty(String iface, String property) throws DBusException {
        Properties properties = objectProperties;
        synchronized(busConnection) {
            if (!primaryInterface.equals(iface)) {
                properties = busConnection.getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Properties.class);
            }
            return (T)properties.Get(iface, property);
        }
    }

    protected <T> void writeProperty(String iface, String property, T value) throws DBusException {
        Properties properties = objectProperties;
        synchronized (context.buslock) {
            if (!primaryInterface.equals(iface)) {
                properties = busConnection.getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Properties.class);
            }
            properties.Set(iface, property, value);
        }
    }

    protected <T> void attemptCachedPropertyUpdate(String property) {
        attemptCachedPropertyUpdate(property, (v) -> { cache.update(property, v); });
    }

    protected <T> void attemptCachedPropertyUpdate(String property, Consumer<T> cacheSetter) {
        try {
            T value = this.<T>readProperty(primaryInterface, property);
            cacheSetter.accept(value);
        } catch (Exception e) {
            getLogger().debug("{}:{} Error reading property, reason: {}", dbusObjectPath, property, e.getMessage());
        }
    }

    public String getPath() {
        // local part only
        return dbusObjectPath;
    }

    public String getDBusIfaceName() {
        return primaryInterface;
    }

    public void suspend(int ttl) {
        inactive = -ttl;
    }

    public void activate() {
        if (inactive < 0) {
            inactive++;
        }
    }

    public void activateNow() {
	inactive = 0;
    }

    public boolean isActive() {
        return inactive >= 0;
    }

    protected abstract void disposeRemote();

    protected abstract void disposeLocal(boolean doRemoteCalls, boolean recurse);
    
    protected void disposeLocalRemoteInterlink() {
        // then destroy interconnects
    }

    protected void dispose(boolean doRemoteCalls, boolean recurse) {
        suspend(1000);
        getLogger().trace("{}: disposal requested", getPath());

        // recurse is not used
        if (doRemoteCalls && allowRemoteCalls) {
            disposeRemote();
        }

        disposeLocal(doRemoteCalls && allowRemoteCalls, recurse);
        disposeLocalRemoteInterlink();
    }

    protected <T> T callWithDispose(Callable<T> call, Runnable disposer) throws Exception, NotReadyException {
        try {
            synchronized (context.buslock) {
                return call.call();
            }
        } catch (NotConnected cause) {
            throw new NotReadyException("Device is not connected " + getPath());
        } catch (DBusExecutionException cause) {
            if (cause.getMessage().matches("^.*Method \".*\" with signature \".*\" on interface .*$")) {
                disposer.run();
            } else if (cause.getMessage().matches("^.*[Nn]ot connected.*$")) {
                throw new NotReadyException("Device is not connected " + getPath());
            } else {
                throw cause;
            }
        } catch (RuntimeException cause) {
            if (cause.getMessage().matches("^.*Method \".*\" with signature \".*\" on interface .*$")) {
                disposer.run();
            } else if (cause.getMessage().matches("^.*[Nn]ot connected.*$")) {
                throw new NotReadyException("Device is not connected " + getPath());
            } else {
                throw cause;
            }
        }
        return null;
    }

    protected void callWithDispose(Runnable call, Runnable disposer) throws NotReadyException {
        try {
            synchronized (context.buslock) {
                call.run();
            }
        } catch (NotConnected cause) {
            throw new NotReadyException("Device is not connected " + getPath());
        } catch (DBusExecutionException cause) {
            if (cause.getMessage().matches("^.*Method \".*\" with signature \".*\" on interface .*$")) {
                disposer.run();
            } else if (cause.getMessage().matches("^.*[Nn]ot connected.*$")) {
                throw new NotReadyException("Device is not connected " + getPath());
            } else {
                throw cause;
            }
        } catch (RuntimeException cause) {
            if (cause.getMessage().matches("^.*Method \".*\" with signature \".*\" on interface .*$")) {
                disposer.run();
            } else if (cause.getMessage().matches("^.*[Nn]ot connected.*$")) {
                throw new NotReadyException("Device is not connected " + getPath());
            } else {
                throw cause;
            }
        }
    }
}
