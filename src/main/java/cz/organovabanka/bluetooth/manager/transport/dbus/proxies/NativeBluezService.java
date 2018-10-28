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
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.GattService1;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezObject;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezService;

import org.freedesktop.DBus;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A class representing Bluez services.
 * @author Lukas Rucka
 */
public class NativeBluezService extends NativeBluezObject implements BluezService {
    private static final Logger logger = LoggerFactory.getLogger(NativeBluezService.class);

    private final URL objectURL;

    public NativeBluezService(BluezContext context, String dbusObjectPath, URL parentDeviceURL) {
        super(context, dbusObjectPath, BluezCommons.BLUEZ_IFACE_SERVICE);
        objectURL = makeURL(parentDeviceURL);
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }   

    @Override
    public URL getURL() {
        return objectURL;
    }

    protected URL makeURL(URL parentDeviceURL) {
        return parentDeviceURL.copyWithService(getUUID());
    }   

    public String getDevicePath() {
        // local part only
        return BluezCommons.parsePath(dbusObjectPath, BluezDevice.class);
    }   

    @Override
    public String getUUID() {
        return this.<String>readProperty("UUID");
    }  

    @Override
    public List<Characteristic> getCharacteristics() {
        List<BluezCharacteristic> discoveredCharacteristics = new ArrayList<>();
        for (BluezHooks.PostCharacteristicDiscoveryHook hook : context.getHooks().getPostCharacteristicDiscoveryHooks()) {
            hook.trigger(this, discoveredCharacteristics, context);
        }
        return discoveredCharacteristics.stream().collect(Collectors.toList());
    }
}

