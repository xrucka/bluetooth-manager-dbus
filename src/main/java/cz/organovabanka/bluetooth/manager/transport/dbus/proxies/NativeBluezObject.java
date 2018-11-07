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
import org.freedesktop.dbus.errors.UnknownMethod;
import org.freedesktop.dbus.errors.UnknownObject;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.exceptions.NotConnected;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.DataConversionUtils;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothInteractionException;
import org.sputnikdev.bluetooth.manager.BluetoothFatalException;
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

    protected NativeBluezObject(BluezContext context, String dbusObjectPath, String primaryInterface) throws BluetoothFatalException {
        this.context = context;
        this.dbusObjectPath = dbusObjectPath;
        this.primaryInterface = primaryInterface;

        try {
            this.objectProperties = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Properties.class);
        } catch (DBusException e) {
            throw new BluetoothFatalException("Unable to access properties of " + dbusObjectPath + ": " + e.getMessage(), e);
        }
    }

    public void commitNotifications(Map<String, Variant<?>> changed) {
        if (getLogger().isInfoEnabled()) {
            String props = changed.entrySet().stream().map((e) -> {
                String key = e.getKey().toString();
                String value = null;
                if ("ay".equals(e.getValue().getSig())) {
                    value = BluezCommons.hexdump((byte[])(e.getValue().getValue()));
                } else {
                    value = e.getValue().getValue().toString();
                }
                return e.getValue().getSig() + ":" + key + "=" + value;
            }).collect(Collectors.joining(", "));
            getLogger().info("Got updated properties of {} ({}) : {}", getPath(), getURL(), props);
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

    protected <T> T readProperty(String property) throws BluetoothInteractionException, BluetoothFatalException {
        getLogger().trace("Reading property {}:{} of {} ({})", primaryInterface, property, getPath(), getURL());
        try {
            return (T)objectProperties.Get(primaryInterface, property);
        } catch (UnknownMethod | UnknownObject | NotConnected ex) {
            getLogger().debug("Reading property failed {}:{} of {} ({}): {}, {}", primaryInterface, property, getPath(), getURL(), ex.getClass().getName() + "/" + ex.getType(), ex.getMessage());
            throw new BluetoothFatalException(ex.toString(), ex);
        } catch (DBusExecutionException ex) {
            getLogger().debug("Reading property failed {}:{} of {} ({}): {}, {}", primaryInterface, property, getPath(), getURL(), ex.getClass().getName() + "/" + ex.getType(), ex.getMessage());
            throw new BluetoothInteractionException(ex.toString() + "/" + ex.getType(), ex);
        }
    }

    protected <T> T readOptionalProperty(String property, Supplier<T> fallbackAction)  throws BluetoothInteractionException, BluetoothFatalException {
        getLogger().trace("Reading optional property {}:{} of {} ({})", primaryInterface, property, getPath(), getURL());
        try {
            return (T)objectProperties.Get(primaryInterface, property);
        } catch (UnknownMethod | UnknownObject | NotConnected ex) {
            getLogger().debug("Reading optional property failed {}:{} of {} ({}): {}, {}", primaryInterface, property, getPath(), getURL(), ex.getClass().getName() + "/" + ex.getType(), ex.getMessage());
            throw new BluetoothFatalException(ex.toString(), ex);
        } catch (DBusExecutionException ex) {
            if (ex.getMessage().contains("No such property")) {
                // do fallback
            } else {
                 getLogger().debug("Reading optional property failed {}:{} of {} ({}): {}, {}", primaryInterface, property, getPath(), getURL(), ex.getClass().getName() + "/" + ex.getType(), ex.getMessage());
                throw new BluetoothInteractionException(ex.toString() + "/" + ex.getType(), ex);
            }
        }

        return fallbackAction.get();
    }

    protected <T> void writeProperty(String iface, String property, T value) throws BluetoothInteractionException, BluetoothFatalException {
        getLogger().trace("Writing property {}:{}={} of {} ({})", iface, property, value.toString(), getPath(), getURL());
        try {
            Properties properties = objectProperties;
            if (!primaryInterface.equals(iface)) {
                properties = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, Properties.class);
            }
            properties.Set(iface, property, value);
        } catch (UnknownMethod | UnknownObject ex) {
            throw new BluetoothFatalException(ex.toString(), ex);
        } catch (DBusException ex) {
            throw new BluetoothInteractionException(ex.toString());
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

    protected <T> T callWithCleanup(Callable<T> call, Runnable disposer) throws BluetoothInteractionException, BluetoothFatalException {
        try {
            return call.call();
        } catch (UnknownObject cause) { 
            // unknown object - probably device deleted/disconnected, etc.
            disposer.run();
            throw new BluetoothFatalException(cause.toString(), cause);
        } catch (DBusExecutionException cause) {
            disposer.run();
            throw new BluetoothInteractionException(cause.getType() + ":" + cause.toString(), cause);
	} catch (BluetoothFatalException | BluetoothInteractionException | NotReadyException passed) {
            // this line ensures that allready processed exceptions in callable do not get processed twice
            throw passed;
        } catch (RuntimeException cause) {
            disposer.run();
            throw cause;
        } catch (Exception cause) {
            disposer.run();
            throw new BluetoothInteractionException("Generic failure on " + getPath() + ": " + cause.getMessage());
        }
    }

    protected void callWithCleanup(Runnable call, Runnable disposer) throws BluetoothInteractionException, BluetoothFatalException {
        try {
            call.run();
         } catch (UnknownObject cause) { 
            // unknown object - probably device deleted/disconnected, etc.
            disposer.run();
            throw new BluetoothFatalException(cause.toString(), cause);
        } catch (DBusExecutionException cause) {
            disposer.run();
            throw new BluetoothInteractionException(cause.getType() + ":" + cause.toString(), cause);
	} catch (BluetoothFatalException | BluetoothInteractionException | NotReadyException passed) {
            // this line ensures that allready processed exceptions in callable do not get processed twice
            throw passed;
        } catch (RuntimeException cause) {
            disposer.run();
            throw cause;
        } catch (Exception cause) {
            disposer.run();
            throw new BluetoothInteractionException("Generic failure on " + getPath() + ": " + cause.getMessage());
        }
    }
}
