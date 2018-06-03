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

import org.freedesktop.dbus.Variant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cz.organovabanka.bluetooth.manager.transport.dbus.interfaces.Properties;

/**
 * Cache class for all Bluez object, effectively ConcurrentHashMap
 * with with update limited to non-url.
 * @author Lukas Rucka
 */
public class PropertyCache implements Properties.Cache {

    private Map<String, Variant> values = new ConcurrentHashMap();

    public PropertyCache() {
        ;
    }

    public <T> void update(String name, T value) {
        if ("url".equals(name) && values.containsKey(name)) {
            return;
        }
        set(name, value);
    }

    public void update(Map<String, Variant> values) {
        for (Map.Entry<String, Variant> newval : values.entrySet()) {
            update(newval.getKey(), newval.getValue());
        }
    }

    public <T> T get(String name) {
        Variant v = values.get(name);
        while ("v".equals(v.getSig())) {
            v = (Variant)v.getValue();
        }
        return (T)(v.getValue());
    }

    public <T> void set(String name, T value) {
        values.put(name, new Variant(value));
    }
}
