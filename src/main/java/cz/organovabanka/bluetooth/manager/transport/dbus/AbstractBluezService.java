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

import cz.organovabanka.bluetooth.manager.transport.dbus.AbstractBluezService;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezCommons;
import cz.organovabanka.bluetooth.manager.transport.dbus.BluezContext;
import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.ObjectManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Device;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * A common class for all bluez-related services.
 * @author Lukas Rucka
 */
public abstract class AbstractBluezService extends BluezObjectBase implements Service {
    protected AbstractBluezService(BluezContext context, String dbusObjectPath, String primaryInterface) {
        super(context, dbusObjectPath, primaryInterface);

        // setup default values of cached attributes
        cache.set("UUID", "invalid-uuid");
        cache.set("url", BluezCommons.DBUSB_PROTOCOL_NAME + "://XX:XX:XX:XX:XX:XX/YY:YY:YY:YY:YY:YY/0000180f-0000-1000-8000-00805f9b34fb");
    }

    public String getDevicePath() {
        // local part only
        return BluezCommons.parsePath(dbusObjectPath, Device.class);
    }   

    public String getUUID() {
        return this.cache.<String>get("UUID");
    }  

    @Override
    public abstract List<Characteristic> getCharacteristics() throws BluezException;

    @Override
    protected void disposeLocal(boolean doRemoteCalls, boolean recurse) {
        // local part
        // nop
    }

    @Override
    public void dispose(boolean doRemoteCalls, boolean recurse) {
        getLogger().debug("{}: Disposing service", getURL().getServiceUUID());
        super.dispose(doRemoteCalls, recurse);
    }

}

