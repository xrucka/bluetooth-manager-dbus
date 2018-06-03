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

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * A class representing Bluez services.
 * @author Lukas Rucka
 */
class BluezService extends BluezObjectBase implements Service {
    private static final Logger logger = LoggerFactory.getLogger(BluezService.class);

    BluezService(BluezContext context, String dbusObjectPath) {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_SERVICE);

        // setup default values of cached attributes
        cache.set("UUID", "invalid-uuid");
        cache.set("url", BluezCommons.DBUSB_PROTOCOL_NAME + "://XX:XX:XX:XX:XX:XX/YY:YY:YY:YY:YY:YY/0000180f-0000-1000-8000-00805f9b34fb");

        updateURL();
    }

    protected Logger getLogger() {
        return logger;
    }   

    protected void updateURL() throws BluezException {
        // this is the remote part of getURL
        try {
            String devicePath = getDevicePath();
            BluezDevice device = new BluezDevice(context, devicePath);
            URL url = device.getURL().copyWithService(getUUID());
            cache.set("url", url.toString());
        } catch (BluezException e) {
            getLogger().error("{}: Unable to update URL, reason: {}", dbusObjectPath, e.getMessage());
        }
    }   

    public String getDevicePath() {
        // local part only
        return BluezCommons.parsePath(dbusObjectPath, BluezDevice.class);
    }   

    protected void disposeRemote() {
        // remote part
        // nop
    }   

    protected void disposeLocal(boolean doRemoteCalls, boolean recurse) {
        // local part
        // nop
    }

    public static void dispose(BluezService service, boolean doRemoteCalls, boolean recurse) {
        logger.debug("{}: Disposing service", service.getURL().getServiceUUID());
        BluezObjectBase.dispose(service, doRemoteCalls, recurse);
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

    @Override
    public List<Characteristic> getCharacteristics() throws BluezException {
        Pattern characteristicPattern = Pattern.compile("^" + this.dbusObjectPath + "/char[0-9a-fA-F]{4}$");

        Map<Path, Map<String, Map<String, Variant>>> allObjects = null;
        try {
            ObjectManager objectManager = busConnection.getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
            allObjects = objectManager.GetManagedObjects();
        } catch (RuntimeException e) {
            throw new BluezException("Unable to access dbus object manager " + dbusObjectPath, e); 
        } catch (DBusException e) {
            throw new BluezException("Unable to enumerate bluetooth objects when processing " + dbusObjectPath, e); 
        }

        try {
            List<BluezCharacteristic> result = allObjects.entrySet().stream()
                .filter((entry) -> { return characteristicPattern.matcher(entry.getKey().toString()).matches(); })
                .map((entry) -> {
                    String objpath = entry.getKey().toString();
                    BluezCharacteristic characteristic = context.getManagedCharacteristic(objpath, true);
    
                    Map<String, Map<String, Variant>> interfaces = entry.getValue();
                    Map<String, Variant> vals = interfaces.get(BluezCommons.BLUEZ_IFACE_CHARACTERISTIC);
                    characteristic.getCache().update(vals);
    
                    return characteristic;
                })
                .collect(Collectors.toList());
            return Collections.unmodifiableList(result);
        } catch (RuntimeException e) {
            throw new BluezException("Unable to unpack bluez objects when processing " + dbusObjectPath, e); 
        }
    }
}

