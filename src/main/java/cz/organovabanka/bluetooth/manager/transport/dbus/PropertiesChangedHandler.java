package cz.organovabanka.bluetooth.manager.transport.dbus;

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

import org.freedesktop.dbus.interfaces.Properties;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.proxies.NativeBluezObject;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;

import org.freedesktop.DBus;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;

/**
 * DBus signal handler for properties update.
 * Upon invocation, fires notification waiting upon target object.
 * @author Lukas Rucka
 */
@DBusInterfaceName("org.freedesktop.DBus.Properties")
public class PropertiesChangedHandler extends AbstractPropertiesChangedHandler {
    private final BluezContext context;
    private final Logger logger = LoggerFactory.getLogger(PropertiesChangedHandler.class);

    public PropertiesChangedHandler(BluezContext context) {
        this.context = context;
    }

    public void handle(Properties.PropertiesChanged signalled) {
        String objpath = signalled.getPath().toString();
        String iface = signalled.getInterfaceName();

        URL targetURL = context.pathURL(iface, objpath);
        if (targetURL == null) {
            return;
        }

        NativeBluezObject target = null;
        //synchronized (context) {
        if (targetURL.isAdapter()) {
            BluezAdapter atarget = context.getManagedAdapter(targetURL);
            if (atarget != null && ! (atarget instanceof NativeBluezObject)) {
                logger.error("BUG: Some other object implementation in place of native bluez object");
                return;
            }
            target = (NativeBluezObject)atarget;
        } else if (targetURL.isDevice()) {
            BluezDevice atarget = context.getManagedDevice(targetURL);
            if (atarget != null && ! (atarget instanceof NativeBluezObject)) {
                logger.error("BUG: Some other object implementation in place of native bluez object");
                return;
            }
            target = (NativeBluezObject)atarget;
        } else if (targetURL.isCharacteristic()) {
            BluezCharacteristic atarget = context.getManagedCharacteristic(targetURL);
            if (atarget != null && ! (atarget instanceof NativeBluezObject)) {
                logger.error("BUG: Some other object implementation in place of native bluez object");
                return;
            }
            target = (NativeBluezObject)atarget;
        }

        if (target == null) { 
            return;
        }

        //synchronized (target) {
        target.commitNotifications(signalled.getPropertiesChanged());
        //}
    }
}

