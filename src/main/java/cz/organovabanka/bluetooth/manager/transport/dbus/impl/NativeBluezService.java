package cz.organovabanka.bluetooth.manager.transport.dbus.impl;

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

import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezService;
import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezContext;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezException;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezFactory;
import cz.organovabanka.bluetooth.manager.transport.dbus.impl.NativeBluezCharacteristic;
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
public class NativeBluezService extends AbstractBluezService {
    private static final Logger logger = LoggerFactory.getLogger(NativeBluezService.class);

    public NativeBluezService(BluezContext context, String dbusObjectPath) {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_SERVICE);

        updateURL();
    }

    protected Logger getLogger() {
        return logger;
    }   

    protected void updateURL() throws BluezException {
        // this is the remote part of getURL
        try {
            String devicePath = getDevicePath();
            AbstractBluezDevice device = context.getManagedDevice(devicePath, false);
            URL url = device.getURL().copyWithService(getUUID());
            cache.set("url", url.toString());
        } catch (BluezException e) {
            getLogger().error("{}: Unable to update URL, reason: {}", dbusObjectPath, e.getMessage());
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
        return super.getUUID();
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
            List<Characteristic> result = allObjects.entrySet().stream()
                .filter((entry) -> { return characteristicPattern.matcher(entry.getKey().toString()).matches(); })
                .map((entry) -> {
                    String objpath = entry.getKey().toString();
                    AbstractBluezCharacteristic characteristic = context.getManagedCharacteristic(objpath, true);
    
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

    protected void disposeRemote() {
        ;
    }

}
