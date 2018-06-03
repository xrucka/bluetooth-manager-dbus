package cz.organovabanka.bluetooth.manager.transport.dbus;

public class BluezException extends RuntimeException {
    public BluezException() {
        super();
    }

    public BluezException(String message) {
        super(message);
    }

    public BluezException(String message, Throwable cause) {
        super(message, cause);
    }
}
