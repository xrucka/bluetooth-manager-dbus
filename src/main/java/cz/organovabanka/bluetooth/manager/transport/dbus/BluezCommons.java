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

import cz.organovabanka.bluetooth.manager.transport.dbus.BluezException;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezAdapter;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezCharacteristic;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezDevice;
import cz.organovabanka.bluetooth.manager.transport.dbus.transport.BluezService;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.Introspectable;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.DataConversionUtils;
import org.sputnikdev.bluetooth.URL;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.EntityResolver;

import java.io.IOException;
import java.io.StringReader;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;

/**
 * Common paths and names for BlueZ.
 * @author Lukas Rucka
 */
public class BluezCommons {
    public static final String BLUEZ_IFACE_ADAPTER = "org.bluez.Adapter1";
    public static final String BLUEZ_IFACE_DEVICE = "org.bluez.Device1";
    public static final String BLUEZ_IFACE_SERVICE = "org.bluez.GattService1";
    public static final String BLUEZ_IFACE_CHARACTERISTIC = "org.bluez.GattCharacteristic1";
    public static final String BLUEZ_IFACE_DESCRIPTOR = "org.bluez.GattDescriptor1";
    public static final String BLUEZ_DBUS_BUSNAME = "org.bluez";
    public static final String BLUEZ_DBUS_OBJECT = "/org/bluez";
    public static final String DBUS_DBUS_BUSNAME = "org.freedesktop.DBus";
    public static final String DBUS_DBUS_OBJECT = "/org/freedesktop/DBus";
    public static final String DBUSB_PROTOCOL_NAME = "bluez";

    private static final Logger logger = LoggerFactory.getLogger(BluezCommons.class);

    public static final Pattern makeAdapterPathPattern() {
        return Pattern.compile("^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+$");
    }

    public static final Pattern makeDevicePathPattern(String adapterPath) {
        return Pattern.compile("^" + adapterPath + "/dev(_[0-9a-fA-F]{2}){6}$");
    }

    public static final Pattern makeServicePathPattern(String devicePath) {
        return Pattern.compile("^" + devicePath + "/service[0-9a-fA-F]{4}$");
    }

    public static final Pattern makeCharacteristicPathPattern(String servicePath) {
        return Pattern.compile("^" + servicePath + "/char[0-9a-fA-F]{4}$");
    }

    public static final String parsePath(String objectPath, Class t) {
        Class[] keys = { BluezAdapter.class, BluezDevice.class, BluezService.class, BluezCharacteristic.class, null };
        String[] patterns = {
            "^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+",
            "^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+/dev(_[0-9a-fA-F]{2}){6}",
            "^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+/dev(_[0-9a-fA-F]{2}){6}/service[0-9a-fA-F]{4}",
            "^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+/dev(_[0-9a-fA-F]{2}){6}/service[0-9a-fA-F]{4}/char[0-9a-fA-F]{4}",
            "^" + BLUEZ_DBUS_OBJECT + "/hci[0-9]+/dev(_[0-9a-fA-F]{2}){6}/service[0-9a-fA-F]{4}/char[0-9a-fA-F]{4}/desc[0-9a-fA-F]{4}$"
        };

        for (int i = 0; i < keys.length; ++i) {
            if (t != keys[i]) {
                continue;
            }
        
            Pattern pattern = Pattern.compile(patterns[i]);
            Matcher matcher = pattern.matcher(objectPath);
            return (matcher.find()) ? matcher.group() : null;
        }
        throw new IllegalArgumentException("Invalid class requested");
    }

    private static String matchBluezObject(
        Map<DBusPath, Map<String, Map<String, Variant<?>>>> allObjects, 
        Pattern pattern, 
        String iface, 
        String key, 
        String value
    ) {
        if (allObjects == null) {
            logger.error("Bluez subsystem not available");
            return null;
        }

        for (Map.Entry<DBusPath, Map<String, Map<String, Variant<?>>>> entry : allObjects.entrySet()) {
            String path = entry.getKey().toString();
            if (!pattern.matcher(path).matches()) {
                continue;
            }

            Map<String, Map<String, Variant<?>>> details = entry.getValue();
            String keyval = details.get(iface).get(key).getValue().toString();

            if (keyval.toLowerCase() == value.toLowerCase()) {
                return path;
            }
        }

        return null;
    }


    public static <T> T readProperty(String iface, String property, Properties props, String objectPath) throws BluezException {
        try {
            return (T)props.Get(iface, property);
        } catch (RuntimeException e) {
            throw new BluezException("Unable to read property " + objectPath + ":" + property + " of: " + e.toString(), e);
        }
    }

    public static <T> void writeProperty(String iface, String property, T value, Properties props, String objectPath) throws BluezException {
        try {
            props.Set(iface, property, value);
        } catch (RuntimeException e) {
            throw new BluezException("Unable to write property " + objectPath + ":" + property + " of: " + e.toString(), e);
        }
    }

    public static void runSilently(Runnable func) {
        try {
            func.run();
        } catch (Exception ignore) { 
            /* do nothing */
        }
    }   

    public static String hexdump(byte[] raw) {
        return DataConversionUtils.convert(raw, 16);
    }

    public static <K> Map<String, String> hexdump(Map<K, byte[]> raw) {
        return raw.entrySet().stream().collect(Collectors.toMap(
            entry -> entry.getKey().toString(),
            entry -> hexdump(entry.getValue())
        ));
    }

    public static List<String> introspectSubpaths(BluezContext context, String rootPath) {
        // https://examples.javacodegeeks.com/core-java/xml/java-xml-parser-tutorial/
        // https://www.baeldung.com/java-xml

        String introspectionXML = null;

        /* populate adapters */
        try {
            Introspectable dbusProbe = context.getDbusConnection().getRemoteObject(BluezCommons.BLUEZ_DBUS_BUSNAME, rootPath, Introspectable.class);
            introspectionXML = dbusProbe.Introspect();	
        } catch (DBusException e) {
            throw new BluezException("Unable to introspect dbus objects underneath blaccess dbus objects to enumerate bluetooth adapters: " + e.getMessage(), e); 
        }

        InputSource stream = new InputSource();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setSchema(null);

        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            logger.error("Could not create document builder for {}: {} {}", rootPath, e.getMessage(), introspectionXML);
            return Collections.emptyList();
        }

        builder.setEntityResolver(new EntityResolver() {
            public InputSource resolveEntity(String publicID, String systemID) throws SAXException, IOException {
                if (systemID.contains("introspect.dtd")) {
                    //return empty source
                    return new InputSource(
                        new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?>".getBytes())
                    );
                } else {
                    return null;
                }
            }
        });

        stream.setCharacterStream(new StringReader(introspectionXML));
        Document introspected = null;
        try {
            introspected = builder.parse(stream);
        } catch (SAXException | IOException e) {
            logger.error("Could not process introspection xml for {}: {} {}", rootPath, e.getMessage(), introspectionXML);
            return Collections.emptyList();
        }

        List<String> subpaths = new ArrayList<>();
        Element rootnode = introspected.getDocumentElement();
        //NodeList entries = rootNode.getElementsByTagName("node");
        NodeList entries = introspected.getElementsByTagName("node");
        // 0 is root node
        for (int i = 1; i < entries.getLength(); ++i) {
            String tpath = rootPath + "/" + ((Element)entries.item(i)).getAttribute("name");
            subpaths.add(tpath);
        }
        
        return subpaths;
    }

}
