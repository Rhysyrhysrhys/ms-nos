package com.workshare.msnos.usvc;

import static com.workshare.msnos.core.Message.Type.ENQ;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.MessageBuilder;
import com.workshare.msnos.soup.time.SystemTime;
import com.workshare.msnos.usvc.api.RestApi;
import com.workshare.msnos.usvc.api.RestApi.Type;

public class Healthchecker {

    public static final String SYSP_CHECK_PERIOD = "msnos.usvc.health.check.time";
    public static final String SYSP_ENQ_PERIOD = "msnos.usvc.health.enq.period";
    
    private static final Logger log = LoggerFactory.getLogger(Healthchecker.class);
    public static final long CHECK_PERIOD = Long.getLong(SYSP_CHECK_PERIOD, 60000L);
    public static final long ENQ_PERIOD = Long.getLong(SYSP_ENQ_PERIOD, 5*CHECK_PERIOD);

    private final Microcloud microcloud;
    private final ScheduledExecutorService scheduler;

    public Healthchecker(Microcloud microcloud, ScheduledExecutorService executorService) {
        this.microcloud = microcloud;
        this.scheduler = executorService;
    }

    public void start() {        
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                healthCheckApis();
                enquiryApis();
            }
        }, CHECK_PERIOD, CHECK_PERIOD, TimeUnit.MILLISECONDS);
    }

    private void healthCheckApis() {
        for (RemoteMicroservice remote : microcloud.getMicroServices()) {
            for (RestApi rest : remote.getApis()) {
                if (rest.getType() == Type.HEALTHCHECK) {
                    HttpURLConnection connection = null;
                    try {
                        connection = (HttpURLConnection) new URL(rest.getUrl()).openConnection();
                        connection.setRequestMethod("HEAD");
                        int responseCode = connection.getResponseCode();
                        if (responseCode != 200) {
                            remote.markFaulty();
                        } else {
                            remote.markWorking();
                        }
                    } catch (Exception ex) {
                        log.error("Unable to health check restApi URL for " + remote, ex);
                        remote.markFaulty();
                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                }
            }
        }
    }

    private void enquiryApis() {
        long now = SystemTime.asMillis();
        for (RemoteMicroservice remote : microcloud.getMicroServices()) {
            if (remote.isFaulty())
                continue;
            
            if ((now - remote.getLastUpdated()) < ENQ_PERIOD)
                continue;
            
            try {
                Message message = new MessageBuilder(ENQ, microcloud.getCloud(), remote.getAgent()).make();
                microcloud.send(message);
            }
            catch (Exception ex) {
                log.error("Unable to send an ENQ to remote agent "+remote.getAgent(), ex);
            }
        }
    }

}
