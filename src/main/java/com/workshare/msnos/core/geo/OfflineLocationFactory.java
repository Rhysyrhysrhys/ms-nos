package com.workshare.msnos.core.geo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.maxmind.geoip2.DatabaseReader;

public class OfflineLocationFactory implements LocationFactory {

    private static final String DB_FILENAME = "geolite2-city.mmdb";
    private DatabaseReader database;

    OfflineLocationFactory() throws IOException {
        this.database = new DatabaseReader.Builder(OfflineLocationFactory.class.getResourceAsStream("/"+DB_FILENAME)).build();
    }

    public OfflineLocationFactory(DatabaseReader database) {
        this.database = database;
    }

    @Override
    public Location make(String host) {
        try {
            return new Location(database.omni(InetAddress.getByName(asValidatedAddress(host))));
        }
        catch (Throwable ignore) {
            return Location.UNKNOWN;
        }
    }

    public DatabaseReader database() {
        return database;
    }

    private String asValidatedAddress(String host) throws UnknownHostException {
        final InetAddress inet = InetAddress.getByName(host);
        final String addr = inet.getHostAddress();
        return addr;
    }

    public static LocationFactory build() {
        try {
            return new OfflineLocationFactory();
        } catch (Exception ex) {
            return new NoopLocationFactory();
        }
    }
}
