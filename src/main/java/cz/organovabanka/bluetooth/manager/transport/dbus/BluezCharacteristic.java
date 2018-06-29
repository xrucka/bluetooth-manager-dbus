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

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.GattCharacteristic1;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import java.util.stream.Stream;

/**
 * A class representing gatt device characteristics (dbus implementation).
 * @author Lukas Rucka
 */
class BluezCharacteristic extends BluezObjectBase implements Characteristic {
    private static final String CONFIGURATION_UUID = "00002902-0000-1000-8000-00805f9b34fb";
    private static final Logger logger = LoggerFactory.getLogger(BluezCharacteristic.class);

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

    private Notification<byte[]> notificationData = null;

    BluezCharacteristic(BluezContext context, String dbusObjectPath) throws BluezException {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_CHARACTERISTIC);

        try {
            this.remoteInterface = busConnection.getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, dbusObjectPath, GattCharacteristic1.class);
        } catch (DBusException e) {
            throw new BluezException("Unable to bind remote object interface on " + dbusObjectPath + ": " + e.getMessage(), e);
        }

	Vector<String> dummy = new Vector();
	dummy.add("none");

        cache.set("Notifying", new Boolean(false));
        cache.set("Flags", new Variant(dummy, "as"));
        cache.set("UUID", "invalid-uuid");
        cache.set("url", BluezCommons.DBUSB_PROTOCOL_NAME 
            + "://XX:XX:XX:XX:XX:XX/YY:YY:YY:YY:YY:YY/0000180f-0000-1000-8000-00805f9b34fb/00002a19-0000-1000-8000-00805f9b34fb");

        setupHandlers();
        updateURL();
    }

    private void setupHandlers() {
        this.handlers.put("Value", (data) -> {
            if (notificationData == null) {
                return;
            }

            BluezFactory.notifySafely(() -> {
                byte[] realData = (byte[])(data.getValue());

                if (getLogger().isTraceEnabled()) {
                    getLogger().trace("{}: Data notification received: {}", dbusObjectPath, DataConversionUtils.convert(realData, 16));
                }
                notificationData.notify(realData);
            }, logger, dbusObjectPath + ":Value");
        });
    }

    protected Logger getLogger() {
        return logger;
    }

    protected void updateURL() throws BluezException {
        // this is the remote part of getURL
        try {
            String servicePath = getServicePath();
            BluezService service = new BluezService(context, servicePath);
            URL url = service.getURL().copyWithCharacteristic(getUUID());
            cache.set("url", url.toString());
        } catch (BluezException e) {
            getLogger().error("{}: Unable to update URL, reason: {}", dbusObjectPath, e.getMessage());
        }
    }

    public String getServicePath() {
        // local part only
        return BluezCommons.parsePath(dbusObjectPath, BluezService.class);
    }

    protected void disposeRemote() {
        // remote part
        if (!allowRemoteCalls) {
            return;
        }

        disableValueNotificationsRemote();
    }

    protected void disposeLocal(boolean doRemoteCalls, boolean recurse) {
        // local part

        // first disable notifications
        disableValueNotifications();
    }

    public static void dispose(BluezCharacteristic obj, boolean doRemoteCalls, boolean recurse) {
        logger.debug("{}:{} Disposing characteristic", obj.dbusObjectPath, obj.getURL().getCharacteristicUUID());
        BluezObjectBase.dispose(obj, doRemoteCalls, recurse);
    }

    private static Set<CharacteristicAccessType> parseFlags(Collection<String> flags) {
        // the very first properties are known:
        // https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.attribute.gatt.characteristico_declaration.xml
        // the rest them to be clarified
        //TODO find out what are they, extended properties (CharacteristicAccessType.EXTENDED_PROPERTIES)?
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

    private void getFlagsRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Vector<String>>attemptCachedPropertyUpdate("Flags", 
            (flags) -> { getCache().update("Flags", new Variant(flags, "as"));}
        );
    }

    @Override
    public Set<CharacteristicAccessType> getFlags() {
        // call remote part
        getFlagsRemote();
        // local part 
        return parseFlags(cache.<Vector<String>>get("Flags"));
    }

    private void isNotifyingRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<Boolean>attemptCachedPropertyUpdate("Notifying");
    }

    @Override
    public boolean isNotifying() throws BluezException {
        // call remote part
        isNotifyingRemote();
        // local part
        return cache.<Boolean>get("Notifying");
    }

    private byte[] readValueRemote() throws BluezException, NotReadyException {
        if (!allowRemoteCalls) {
            getLogger().debug("{}: Not allowed to make remote calls", dbusObjectPath);
            return null;
        }

        final Map<String, Variant> options = new HashMap<String, Variant>();

        try {
            return this.<byte[]>callWithDispose(
                () -> { return (byte[])(remoteInterface.ReadValue(options)); },
                () -> { allowRemoteCalls = false; context.disposeCharacteristic(dbusObjectPath, false, true); }
            );
        } catch (NotReadyException e) {
            throw e;
        } catch (RuntimeException e) {
            getLogger().error("gonzales: {}: failed read characteristic call, A {} thread: {}", dbusObjectPath, e.getClass().getName(), Thread.currentThread().getId());
            Stream.of(Thread.currentThread().getStackTrace()).forEach(
                (elem) -> {
                    getLogger().error("gonzales: {}:\t\t{}", Thread.currentThread().getId(), elem.toString());
                });
           
            throw new BluezException("Unable to read value of " + dbusObjectPath + ": " + e.getClass().getName() + ":" + e.getMessage(), e);
        } catch (Exception e) {
            
            getLogger().error("gonzales: {}: failed read characteristic call, B thread: {}", dbusObjectPath, Thread.currentThread().getId());
            Stream.of(Thread.currentThread().getStackTrace()).forEach(
                (elem) -> {
                    getLogger().error("gonzales: {}:\t\t{}", Thread.currentThread().getId(), elem.toString());
                });
            
            throw new BluezException("Unable to read value of " + dbusObjectPath + ": " + e.getClass().getName() + ":" + e.getMessage(), e);
        }
    }

    @Override
    public byte[] readValue() throws BluezException, NotReadyException {
        getLogger().debug("{}: Reading value", dbusObjectPath);
        byte[] value = null;

        value = readValueRemote();

        if (getLogger().isTraceEnabled()) {
            getLogger().trace("{}: Value read: {}", dbusObjectPath, DataConversionUtils.convert(value, 16));
        }
        cache.update("Value", new Variant(value, "ay"));

        return value;
    }

    private void enableValueNotificationsRemote() throws BluezException {
        if (!allowRemoteCalls) {
            getLogger().debug("{}: Not allowed to make remote calls", dbusObjectPath);
            return;
        }

        try {
            callWithDispose(
                () -> { remoteInterface.StartNotify(); },
                () -> { allowRemoteCalls = false; context.disposeCharacteristic(dbusObjectPath, false, true); }
            );
        } catch (RuntimeException e) {
            throw new BluezException("Unable to start notification for " + dbusObjectPath + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void enableValueNotifications(Notification<byte[]> notification) throws BluezException {
        getLogger().trace("{}: Enable value notifications", dbusObjectPath);
        enableValueNotificationsRemote();
        notificationData = notification;
    }

    private void disableValueNotificationsRemote() throws BluezException {
        if (!allowRemoteCalls) {
            getLogger().debug("{}: Not allowed to make remote calls", dbusObjectPath);
            return;
        }

        try {
            callWithDispose(
                () -> { remoteInterface.StopNotify(); }, 
                () -> { allowRemoteCalls = false; context.disposeCharacteristic(dbusObjectPath, false, true); }
            );
        } catch (RuntimeException e) {
            throw new BluezException("Unable to start notification for " + dbusObjectPath + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void disableValueNotifications() throws BluezException {
        getLogger().trace("{}: Disable value notifications", dbusObjectPath);
        notificationData = null;
        disableValueNotificationsRemote();
    }

    private void writeValueRemote(byte[] bytes) throws BluezException {
        if (!allowRemoteCalls) {
            getLogger().debug("{}: Not allowed to make remote calls", dbusObjectPath);
            return;
        }

        Map<String, Variant> options = Collections.emptyMap();

        try {
            callWithDispose(
                () -> { remoteInterface.WriteValue(bytes, options); },
                () -> { allowRemoteCalls = false; context.disposeCharacteristic(dbusObjectPath, false, true); }
            );
        } catch (RuntimeException e) {
            throw new BluezException("Unable to commit write for " + dbusObjectPath + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean writeValue(byte[] bytes) throws BluezException {
        getLogger().debug("{}: Writing value", dbusObjectPath);
        writeValueRemote(bytes);
        return true;
    }

    @Override
    public boolean isNotificationConfigurable() {
        // has only local version, as remote calls are done only as scans
        Pattern descriptorPattern = Pattern.compile("^" + this.dbusObjectPath + "/descriptor[0-9a-fA-F]+$");

        try {
            ObjectManager objectManager = busConnection.getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);

            Map<Path, Map<String, Map<String, Variant>>> allObjects = objectManager.GetManagedObjects();
            if (allObjects == null) {
                return false;
            }

            for (Map.Entry<Path, Map<String, Map<String, Variant>>> entry : allObjects.entrySet()) {
                if (!descriptorPattern.matcher(entry.getKey().toString()).matches()) {
                    continue;
                }

                Map<String, Map<String, Variant>> details = entry.getValue();
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


    private void getUUIDRemote() {
        // remote - update cache
        // property - no action if read fails
        this.<String>attemptCachedPropertyUpdate("UUID");
    }

    public String getUUID() {
        // call remote part
        if (allowRemoteCalls) {
            getUUIDRemote();
        }   
        // local part
        return this.cache.<String>get("UUID");
    }   
}
