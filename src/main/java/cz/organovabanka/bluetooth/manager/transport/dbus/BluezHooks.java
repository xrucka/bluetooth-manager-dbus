package cz.organovabanka.bluetooth.manager.transport.dbus;

/*-
 * #%L
 * 
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

import cz.organovabanka.bluetooth.manager.transport.dbus.BluezContext;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Wrap class containing hooks to trigger upon internal events
 * @author Lukas Rucka
 */
public class BluezHooks {
    public interface PostAdapterDiscoveryHook {
        void trigger(BluezContext context, List<BluezAdapter> discoveredAdapters);
    }

    private final List<PostAdapterDiscoveryHook> postAdapterDiscoveryHooks = new ArrayList<>();

    public interface PostDeviceDiscoveryHook {
        void trigger(BluezContext context, BluezAdapter parentAdapter, List<BluezDevice> discoveredDevices);
    }

    private final List<PostDeviceDiscoveryHook> postDeviceDiscoveryHooks = new ArrayList<>();

    public interface PostServiceDiscoveryHook {
        void trigger(BluezContext context, BluezDevice parentDevice, List<BluezService> discoveredServices);
    }

    private final List<PostServiceDiscoveryHook> postServiceDiscoveryHooks = new ArrayList<>();

    public interface PostCharacteristicDiscoveryHook {
        void trigger(BluezContext context, BluezService parentService, List<BluezCharacteristic> discoveredCharacteristics);
    }

    private final List<PostCharacteristicDiscoveryHook> postCharacteristicDiscoveryHooks = new ArrayList<>();

    public void addPostAdapterDiscoveryHook(PostAdapterDiscoveryHook nhook) {
        postAdapterDiscoveryHooks.add(nhook);
    }

    public void addPostDeviceDiscoveryHook(PostDeviceDiscoveryHook nhook) {
        postDeviceDiscoveryHooks.add(nhook);
    }

    public void addPostServiceDiscoveryHook(PostServiceDiscoveryHook nhook) {
        postServiceDiscoveryHooks.add(nhook);
    }

    public void addPostCharacteristicDiscoveryHook(PostCharacteristicDiscoveryHook nhook) {
        postCharacteristicDiscoveryHooks.add(nhook);
    }

    public Collection<PostAdapterDiscoveryHook> getPostAdapterDiscoveryHooks() {
        return Collections.unmodifiableList(postAdapterDiscoveryHooks);
    }

    public Collection<PostDeviceDiscoveryHook> getPostDeviceDiscoveryHooks() {
        return Collections.unmodifiableList(postDeviceDiscoveryHooks);
    }

    public Collection<PostServiceDiscoveryHook> getPostServiceDiscoveryHooks() {
        return Collections.unmodifiableList(postServiceDiscoveryHooks);
    }

    public Collection<PostCharacteristicDiscoveryHook> getPostCharacteristicDiscoveryHooks() {
        return Collections.unmodifiableList(postCharacteristicDiscoveryHooks);
    }
}
