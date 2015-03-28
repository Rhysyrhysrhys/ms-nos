package com.workshare.msnos.core;

import static com.workshare.msnos.soup.Shorteners.shorten;

import java.util.Collections;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workshare.msnos.core.Cloud.Internal;
import com.workshare.msnos.core.Cloud.Listener;
import com.workshare.msnos.core.cloud.MessagePreProcessors;
import com.workshare.msnos.core.cloud.MessagePreProcessors.Result;
import com.workshare.msnos.core.cloud.Multicaster;
import com.workshare.msnos.soup.json.Json;

public class Receiver {

    private static final Logger log = LoggerFactory.getLogger(Receiver.class);
    private static final Logger proto = LoggerFactory.getLogger("protocol");

    private final Cloud cloud;
    private final Set<Gateway> gates;
    private final Multicaster caster;
    private final MessagePreProcessors validators;
    private final Internal internal;

    
    Receiver(Cloud cloud, Set<Gateway> gates, Multicaster multicaster) {
        this.cloud = cloud;
        this.caster = multicaster;
        this.gates = Collections.unmodifiableSet(gates);
        this.internal = cloud.internal();
        this.validators = new MessagePreProcessors(internal);

        for (final Gateway gate : gates) {
            gate.addListener(this.cloud, new Gateway.Listener() {
                @Override
                public void onMessage(Message message) {
                    process(message, gate.name());
                }
            });
        }
    }

    public Set<Gateway> gateways() {
        return gates;
    }

    public Multicaster caster() {
        return caster;
    }

    public Listener addListener(Listener listener) {
        log.debug("Adding listener: {}", listener);
        return caster.addListener(listener);
    }

    public void removeListener(Listener listener) {
        log.debug("Removing listener: {}", listener);
        caster.removeListener(listener);
    }

    public void process(Message message, String gateName) {
        Result result = validators.isValid(message);
        if (result.success()) {
            logRX(message, gateName);

            message.getData().process(message, internal);

            caster.dispatch(message);
            cloud.postProcess(message);
        } else {
            logNN(message, gateName, result.reason());
        }
    }

    private void logNN(Message msg, String gateName, String cause) {
        if (!proto.isDebugEnabled())
            return;

        final String muid = shorten(msg.getUuid());
        final String payload = Json.toJsonString(msg.getData());
        final String mseq = shorten(msg.getSequence());

        Iden from = msg.getFrom();
        if (internal.localAgents().containsKey(from))
            proto.trace("NN({}): ={}= {} {} {} {} {} {}", shorten(gateName,3), cause, msg.getType(), muid, mseq, msg.getFrom(), msg.getTo(), payload);
        else
            proto.debug("NN({}): ={}= {} {} {} {} {} {}", shorten(gateName,3), cause, msg.getType(), muid, mseq, msg.getFrom(), msg.getTo(), payload);
    }

    private void logRX(Message msg, String gateName) {
        if (!proto.isInfoEnabled())
            return;

        final String muid = shorten(msg.getUuid());
        final String payload = Json.toJsonString(msg.getData());
        final String mseq = shorten(msg.getSequence());
        proto.info("RX({}): {} {} {} {} {} {}", shorten(gateName,3), msg.getType(), muid, mseq, msg.getFrom(), msg.getTo(), payload);
    }
}