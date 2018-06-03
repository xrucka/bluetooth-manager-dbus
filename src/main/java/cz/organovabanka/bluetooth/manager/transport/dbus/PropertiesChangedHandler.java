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
import org.freedesktop.dbus.DBusInterfaceName;
import org.freedesktop.dbus.DBusSigHandler;

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Properties;

/**
 * DBus signal handler for cached properties update.
 * Upon invocation, it first updates the target object cache and then
 * invokes notification waiting upon target object.
 * @author Lukas Rucka
 */
@DBusInterfaceName("org.freedesktop.DBus.Properties")
public class PropertiesChangedHandler implements DBusSigHandler<Properties.PropertiesChanged> {
    private final BluezContext context;

    public PropertiesChangedHandler(BluezContext context) {
        this.context = context;
    }

    public void handle(Properties.PropertiesChanged signalled) {
        String objpath = signalled.getPath().toString();
        BluezObjectBase target = null;

        //synchronized (context) {
            if (objpath.equals(BluezCommons.parsePath(objpath, BluezAdapter.class))) {
                target = context.getManagedAdapter(objpath, false);
            } else if (objpath.equals(BluezCommons.parsePath(objpath, BluezDevice.class))) {
                target = context.getManagedDevice(objpath, false);
            } else if (objpath.equals(BluezCommons.parsePath(objpath, BluezCharacteristic.class))) {
                target = context.getManagedCharacteristic(objpath, false);
            }
        //}

        if (target == null) { 
            return;
        }

        //synchronized (target) {
            target.activateNow();
            target.getCache().update(signalled.getPropertiesChanged());
            target.commitNotifications(signalled.getPropertiesChanged());
        //}
    }
}

