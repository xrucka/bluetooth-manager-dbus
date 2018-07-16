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
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.DataConversionUtils;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Notification;
import org.sputnikdev.bluetooth.manager.transport.Service;

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.GattCharacteristic1;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import java.util.stream.Stream;

/**
 * A common class form implementing Characteristic as bluez-related object
 * @author Lukas Rucka
 */
public abstract class AbstractBluezCharacteristic extends BluezObjectBase implements Characteristic {
    protected static final String CONFIGURATION_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    protected enum AccessTypeMapping {
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

    protected Notification<byte[]> notificationData = null;

    protected AbstractBluezCharacteristic(BluezContext context, String dbusObjectPath, String primaryInterface) throws BluezException {
        super(context, dbusObjectPath, primaryInterface);

    	Vector<String> dummy = new Vector();
        dummy.add("none");

        cache.set("Notifying", new Boolean(false));
        cache.set("Flags", new Variant(dummy, "as"));
        cache.set("UUID", "invalid-uuid");
        cache.set("url", BluezCommons.DBUSB_PROTOCOL_NAME 
            + "://XX:XX:XX:XX:XX:XX/YY:YY:YY:YY:YY:YY/0000180f-0000-1000-8000-00805f9b34fb/00002a19-0000-1000-8000-00805f9b34fb");

   }

    protected abstract Logger getLogger();

    protected abstract void updateURL() throws BluezException;

    public String getServicePath() {
        // local part only
        return BluezCommons.parsePath(dbusObjectPath, Service.class);
    }

    protected abstract void disposeRemote();

    @Override
    protected void disposeLocal(boolean doRemoteCalls, boolean recurse) {
        // local part

        // first disable notifications
        disableValueNotifications();
    }

    @Override
    public void dispose(boolean doRemoteCalls, boolean recurse) {
        getLogger().debug("{}:{} Disposing characteristic", dbusObjectPath, getURL().getCharacteristicUUID());
        super.dispose(doRemoteCalls, recurse);
    }

    protected static Set<CharacteristicAccessType> parseFlags(Collection<String> flags) {
        /*
        "broadcast"
        "read"
        "write-without-response"
        "write"
        "notify"
        "indicate"
        "authenticated-signed-writes"

        "reliable-write"
        "writable-auxiliaries"
        "encrypt-read"
        "encrypt-write"
        "encrypt-authenticated-read"
        "encrypt-authenticated-write"
        "secure-read" (Server only)
        "secure-write" (Server only)
         */
        return flags.stream()
            .map(flag -> AccessTypeMapping.valueOf(flag.toLowerCase().replaceAll("-", "_")).getAccessType())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<CharacteristicAccessType> getFlags() {
        return parseFlags(cache.<Vector<String>>get("Flags"));
    }

    @Override
    public boolean isNotifying() throws BluezException {
        return cache.<Boolean>get("Notifying");
    }

    @Override
    public abstract byte[] readValue() throws BluezException, NotReadyException;

    @Override
    public void enableValueNotifications(Notification<byte[]> notification) throws BluezException {
        getLogger().trace("{}: Enable value notifications", dbusObjectPath);
        notificationData = notification;
    }

    @Override
    public void disableValueNotifications() throws BluezException {
        getLogger().trace("{}: Disable value notifications", dbusObjectPath);
        notificationData = null;
    }

    @Override
    public boolean writeValue(byte[] bytes) throws BluezException {
        getLogger().debug("{}: Writing value", dbusObjectPath);
        return true;
    }

    @Override
    public abstract boolean isNotificationConfigurable();

    public String getUUID() {
        return this.cache.<String>get("UUID");
    }
}
