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
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezFactory;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezHooks;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.GattCharacteristic1;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezObject;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezService;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.DataConversionUtils;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import java.util.ArrayList;
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
public class NativeBluezCharacteristic extends NativeBluezObject implements BluezCharacteristic {
    private static final String CONFIGURATION_UUID = "00002902-0000-1000-8000-00805f9b34fb";
    private static final Logger logger = LoggerFactory.getLogger(NativeBluezCharacteristic.class);

    private enum AccessTypeMapping {
        broadcast(CharacteristicAccessType.BROADCAST),
        read(CharacteristicAccessType.READ),
        write_without_response(CharacteristicAccessType.WRITE_WITHOUT_RESPONSE),
        write(CharacteristicAccessType.WRITE),
        notify(CharacteristicAccessType.NOTIFY),
        indicate(CharacteristicAccessType.INDICATE),
        authenticated_signed_writes(CharacteristicAccessType.AUTHENTICATED_SIGNED_WRITES),

        reliable_write(null),
        writable_auxiliaries(null),
        encrypt_read(null),
        encrypt_write(null),
        encrypt_authenticated(null),
        encrypt_authenticated_write(null),
        secure_read(null),
        secure_write(null);

        private final CharacteristicAccessType accessType;

        AccessTypeMapping(CharacteristicAccessType accessType) {
            this.accessType = accessType;
        }

        CharacteristicAccessType getAccessType() {
            return accessType;
        }
    }

    private final GattCharacteristic1 remoteInterface;
    private final URL objectURL;

    private Notification<byte[]> notificationData = null;

    public NativeBluezCharacteristic(BluezContext context, String dbusObjectPath, URL parentServiceURL) throws BluezException {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_CHARACTERISTIC);

        try {
            this.remoteInterface = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, GattCharacteristic1.class);
        } catch (DBusException e) {
            throw new BluezException("Unable to bind remote object interface on " + dbusObjectPath + ": " + e.getMessage(), e);
        }

        objectURL = makeURL(parentServiceURL);
        setupHandlers();
    }

    private void setupHandlers() {
        this.handlers.put("Value", (data) -> {
            if (notificationData == null) {
                return;
            }

            context.notifySafely(() -> {
                byte[] realData = (byte[])(data.getValue());

                if (getLogger().isTraceEnabled()) {
                    getLogger().trace("{}: Data notification received: {}", dbusObjectPath, DataConversionUtils.convert(realData, 16));
                }
                notificationData.notify(realData);
            }, logger, dbusObjectPath + ":Value");
        });
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public URL getURL() {
        return objectURL;
    }

    protected URL makeURL(URL parentServiceURL) {
        return parentServiceURL.copyWithCharacteristic(getUUID());
    }

    public String getServicePath() {
        // local part only
        return BluezCommons.parsePath(dbusObjectPath, BluezService.class);
    }

    @Override
    public void dispose() {
        disableValueNotifications();
        super.dispose();
    }

    private static Set<CharacteristicAccessType> parseFlags(Collection<String> flags) {
        return flags.stream()
            .map(flag -> AccessTypeMapping.valueOf(flag.toLowerCase().replaceAll("-", "_")).getAccessType())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<CharacteristicAccessType> getFlags() {
        ArrayList<String> gattFlags = this.<ArrayList<String>>readProperty("Flags");
        return parseFlags(gattFlags);
    }

    @Override
    public boolean isNotifying() {
        return this.<Boolean>readOptionalProperty("Notifying", () -> new Boolean(false));
    }

    @Override
    public byte[] readValue() {
        getLogger().trace("Invoking {}() of {} ({})", "readValue", getPath(), getURL());

        final Map<String, Variant<?>> options = new HashMap<String, Variant<?>>();
        return this.<byte[]>callWithCleanup(
            () -> { return (byte[])(remoteInterface.ReadValue(options)); },
            () -> { context.dropCharacteristic(getURL()); }
        );
    }

    @Override
    public void enableValueNotifications(Notification<byte[]> notification) {
        notificationData = notification;
        BluezCommons.runSilently(() -> { remoteInterface.StartNotify(); });
/*
        callWithCleanup(
            () -> { remoteInterface.StartNotify(); },
            () -> { context.dropCharacteristic(getURL()); }
        );
*/
    }

    @Override
    public void disableValueNotifications() {
        BluezCommons.runSilently(() -> { remoteInterface.StopNotify(); });
        notificationData = null;
/*
        callWithCleanup(
            () -> { remoteInterface.StopNotify(); }, 
            () -> { context.dropCharacteristic(getURL()); }
        );
*/
    }

    @Override
    public boolean writeValue(byte[] bytes) {
        getLogger().trace("Invoking {}() of {} ({})", "writeValue", getPath(), getURL());

        Map<String, Variant<?>> options = Collections.emptyMap();
        callWithCleanup(
            () -> { remoteInterface.WriteValue(bytes, options); },
            () -> { context.dropCharacteristic(getURL()); }
        );

        // todo - wait for write confirmation
        return true;
    }

    @Override
    public boolean isNotificationConfigurable() {
        // has only local version, as remote calls are done only as scans
        Pattern descriptorPattern = Pattern.compile("^" + this.dbusObjectPath + "/descriptor[0-9a-fA-F]+$");

        try {
            ObjectManager objectManager = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);

            Map<DBusPath, Map<String, Map<String, Variant<?>>>> allObjects = objectManager.GetManagedObjects();
            if (allObjects == null) {
                return false;
            }

            for (Map.Entry<DBusPath, Map<String, Map<String, Variant<?>>>> entry : allObjects.entrySet()) {
                if (!descriptorPattern.matcher(entry.getKey().toString()).matches()) {
                    continue;
                }

                Map<String, Map<String, Variant<?>>> details = entry.getValue();
                String uuid = details.get(primaryInterface).get("UUID").getValue().toString();
                if (CONFIGURATION_UUID.equalsIgnoreCase(uuid)) {
                    return true;
                }
            }
            
            return false;
        } catch (DBusException e) {
            getLogger().error("{}: Unable to read descriptor", dbusObjectPath); 
            return false;
        }
    }

    @Override
    public String getUUID() {
        return this.<String>readProperty("UUID");
    }   
}
