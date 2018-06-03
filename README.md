# bluetooth-manager-dbus
A transport implementation for the [Bluetooth Manager](https://github.com/sputnikdev/bluetooth-manager) based on [Dbus](https://dbus.freedesktop.org) message subsystem and it's java binding.

## Prerequisites

Tested on Bluez 5.49 on OpenSuSE Leap 42.3 aarch64 on Raspberry PI, with both local blueztooth adapter and adapter shared over USBIP.
If you need to operate on older bluez, you'll need to run bluetoothd (bluez daemon) with --expermental.

---
## Contribution

### Building

Then build the project with maven:
```bash
mvn clean install
```

For use in OpenHab, you'll need to build corresponding [openhab plugin](https://github.com/xrucka/eclipse-smarthome-bluetooth-binding-dbus-transport).
