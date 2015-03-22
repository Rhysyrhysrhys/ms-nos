package com.workshare.msnos.usvc.api;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonObject;
import com.workshare.msnos.core.Agent;
import com.workshare.msnos.core.protocols.ip.Endpoint;
import com.workshare.msnos.core.protocols.ip.Network;
import com.workshare.msnos.soup.json.Json;

public class RestApi {

    public enum Type {PUBLIC, INTERNAL, HEALTHCHECK, MSNOS_HTTP}

    private static final AtomicLong NEXT_ID = new AtomicLong(0);

    private final String name;
    private final String path;
    private final String host;
    private final int port;
    private final boolean sticky;
    private final Type type;
    private final int priority;

    private final transient AtomicInteger tempFaults;
    private final transient long id;

    private transient boolean faulty;


    public RestApi(String name, String path, int port) {
        this(name, path, port, null);
    }

    public RestApi(String name, String path, int port, String host) {
        this(name, path, port, host, Type.PUBLIC, false);
    }

    public RestApi(String name, String path, int port, String host, Type type, boolean sessionAffinity) {
        this(name, path, port, host, type, sessionAffinity, 0);
    }

    public RestApi(String name, String path, int port, String host, Type type, boolean sessionAffinity, int priority) {
        if (path == null) {
            throw new IllegalArgumentException("path cannot be null");
        }
        this.faulty = false;
        this.name = name;
        this.path = path;
        this.port = port;
        this.host = host;
        this.sticky = sessionAffinity;
        this.type = type;
        this.priority = priority;
        this.id = NEXT_ID.getAndIncrement();
        tempFaults = new AtomicInteger();
    }

    public RestApi asHealthCheck() {
        return new RestApi(name, path, port, host, Type.HEALTHCHECK, sticky, priority);
    }

    public RestApi asInternal() {
        return new RestApi(name, path, port, host, Type.INTERNAL, sticky, priority);
    }

    public RestApi withPriority(int priority) {
        return new RestApi(name, path, port, host, type, sticky, priority);
    }

    public RestApi onHost(String host) {
        return new RestApi(name, path, port, host, type, sticky, priority);
    }

    public RestApi onPort(int port) {
        return new RestApi(name, path, port, host, type, sticky, priority);
    }

    public RestApi withAffinity() {
        return new RestApi(name, path, port, host, type, true, priority);
    }

    public boolean hasAffinity() {
        return sticky;
    }

    public boolean isFaulty() {
        return faulty;
    }

    public RestApi markFaulty() {
        faulty = true;
        return this;
    }

    public void markWorking() {
        tempFaults.set(0);
        faulty = false;
    }

    public RestApi markTempFault() {
        tempFaults.incrementAndGet();
        return this;
    }

    public int getTempFaults() {
        return tempFaults.get();
    }

    public int getPriority() {
        return priority;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUrl() {
        String hostname = getHost();
        if (hostname == null)
            hostname = "*";
        
        String prefix = "";
        String path = getPath();
        if (!path.startsWith("/"))
            prefix = "/";
        
        return String.format("http://%s:%d%s%s", hostname, getPort(), prefix, getPath());
    }

    public long getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        int prime = 31;
        result = prime * result + path.hashCode();
        result = prime * result + port;
        result = prime * result + (sticky ? 1231 : 1237);
        result = prime * result + type.hashCode();
        result = prime * result + ((host == null) ? 0 : host.hashCode());
        return result;
    }

    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RestApi restApi = (RestApi) o;

        if (port != restApi.port) return false;
        if (sticky != restApi.sticky) return false;
        if (!name.equals(restApi.name)) return false;
        if (!path.equals(restApi.path)) return false;
        if (type != restApi.type) return false;
        if (!host.equals(restApi.host)) return false;

        return true;
    }

    @Override
    public String toString() {
        try {
            JsonObject obj = (JsonObject)Json.toJsonTree(this);
            obj.addProperty("faulty",this.isFaulty());
            obj.addProperty("tempfaults",this.getTempFaults());
            return obj.toString();
        } catch (Exception any) {
            return super.toString();
        }
    }

    public static Set<RestApi> ensureHostIsPresent(Agent agent, Set<RestApi> apis) {
        Set<RestApi> result = new HashSet<RestApi>();
        for (RestApi api : apis) {
            if (api.getHost() == null || api.getHost().isEmpty()) {
                for (Endpoint endpoint : agent.getEndpoints()) {
                    Network network = endpoint.getNetwork();
                    result.add(api.onHost(network.getHostString()));
                }
            } else {
                result.add(api);
            }
        }
        return result;
    }

}
