package com.workshare.msnos.core;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workshare.msnos.core.Gateway.Listener;
import com.workshare.msnos.core.MsnosException.Code;
import com.workshare.msnos.core.protocols.ip.Endpoint;
import com.workshare.msnos.core.protocols.ip.HttpClientFactory;
import com.workshare.msnos.core.protocols.ip.MulticastSocketFactory;
import com.workshare.msnos.core.protocols.ip.http.HttpGateway;
import com.workshare.msnos.core.protocols.ip.udp.UDPGateway;
import com.workshare.msnos.core.protocols.ip.udp.UDPServer;
import com.workshare.msnos.core.protocols.ip.www.WWWGateway;
import com.workshare.msnos.core.serializers.WireJsonSerializer;
import com.workshare.msnos.soup.ShutdownHooks;
import com.workshare.msnos.soup.ShutdownHooks.Hook;
import com.workshare.msnos.soup.threading.ExecutorServices;
import com.workshare.msnos.soup.threading.Multicaster;

public class Gateways {
    
    public static final String SYSP_GATE_WWW_DISABLE =  "msnos.core.gateways.www.disable";
    public static final String SYSP_GATE_UDP_DISABLE  = "msnos.core.gateways.udp.disable";
    public static final String SYSP_GATE_HTTP_DISABLE = "msnos.core.gateways.http.disable";

    private static Logger log = LoggerFactory.getLogger(Gateways.class);

    private static  Set<Gateway> all = new CopyOnWriteArraySet<Gateway>();
    public static final Set<Gateway> NONE = Collections.<Gateway> emptySet();

	public static Set<Gateway> all() throws MsnosException {
		if (all.size() == 0) {
			addGateway(buildUDPGateway());
            addGateway(buildWWWGateway());
            addGateway(buildHttpGateway());

            if (all.size() == 0)
                throw new MsnosException("Unable to create at least one gateway", Code.UNRECOVERABLE_FAILURE);
            
			addShutdownHook();
		}
		
		return all;
	}
	
    public static Set<Endpoint> allEndpoints() throws MsnosException {
        HashSet<Endpoint> points = new HashSet<Endpoint>();
	    for (Gateway gate : all()) {
            points.addAll(gate.endpoints().all());
        }
	    
	    return points;
	}

    public static Set<Endpoint> endpointsOf(Agent agent) throws MsnosException {        
        HashSet<Endpoint> points = new HashSet<Endpoint>();
        for (Gateway gate : all()) {
            points.addAll(gate.endpoints().of(agent));
        }
        
        return points;
    }

    public static Set<Endpoint> allPublicEndpoints() throws MsnosException {
        HashSet<Endpoint> points = new HashSet<Endpoint>();
        for (Gateway gate : all()) {
            points.addAll(gate.endpoints().publics());
        }
        
        return points;
    }

    private static void addGateway(final Gateway gateway) {
        if (gateway != null)
            all.add(gateway);
    }

    private static void addShutdownHook() {
        ShutdownHooks.addHook(new Hook() {
		    @Override
		    public void run() {
                log.info("Closing gateways...");
		        for (Gateway gate : all)
		            close(gate);
                log.info("done!");
		    }

            private void close(Gateway gate) {
                final String name = gate.getClass().getSimpleName();
                try {
                    log.info("- closing gateway "+name+"...");
                    gate.close();
                } catch (IOException ex) {
                    log.warn("Unexpected exception closing gateway "+name);
                }
            }

            @Override
            public String name() {
                return "Gateway closer";
            }

            @Override
            public int priority() {
                return -1000;
            }
		});
    }

    private static Gateway buildHttpGateway() {
        if (Boolean.getBoolean(SYSP_GATE_HTTP_DISABLE)) {
            log.warn("HTTP Gateway disabled by system property!");
            return null;
        }
        else
            return new HttpGateway(newHttpClient());
    }

    private static UDPGateway buildUDPGateway() {
        if (Boolean.getBoolean(SYSP_GATE_UDP_DISABLE)) {
            log.warn("UDP Gateway disabled by system property!");
            return null;
        }
        
        try {
            final UDPServer server = new UDPServer();
            final MulticastSocketFactory sockets = new MulticastSocketFactory();
    		return new UDPGateway(sockets, server, newMulticaster());
        } catch (Throwable ex) {
            log.error("Unable to create UDP gateway", ex);
            return null;
        }
    }

    private static WWWGateway buildWWWGateway() {
        if (Boolean.getBoolean(SYSP_GATE_WWW_DISABLE)) {
            log.warn("WWW Gateway disabled by system property!");
            return null;
        }
        
        if (!System.getProperties().containsKey(WWWGateway.SYSP_ADDRESS)) {
            log.warn("Missing configuration for WWW gateway, please add property "+WWWGateway.SYSP_ADDRESS);
            return null;
        }
        
        try {
            WWWGateway gate = new WWWGateway(newHttpClient(), newScheduler(), new WireJsonSerializer(), newMulticaster()); 
            log.info("Succesfully connected to WWW gateway at {}", gate.root());
            return gate;
        } catch (Throwable ex) {
            log.error("Unable to create WWW gateway", ex);
            return null;
        }
    }

    private static HttpClient newHttpClient() {
        return HttpClientFactory.newHttpClient();
    }

    private static ScheduledExecutorService newScheduler() {
        return ExecutorServices.newSingleThreadScheduledExecutor();
    }

    private static Multicaster<Listener, Message> newMulticaster() {
        final Multicaster<Listener, Message> caster = new Multicaster<Gateway.Listener, Message>() {
			@Override
			protected void dispatch(Gateway.Listener listener, Message message) {
				listener.onMessage(message);
			}
		};
        return caster;
    }

    static void reset() {
        log.warn("Somebody reset the gateways!");
        all = new CopyOnWriteArraySet<Gateway>();
    }
}
