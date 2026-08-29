package org.apache.cloudberry.pxf.service.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.cloudberry.pxf.api.model.ConfigurationFactory;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.api.utilities.Utilities;
import org.apache.cloudberry.pxf.service.MetricsReporter;
import org.apache.cloudberry.pxf.service.activity.ActiveRequestRegistry;
import org.apache.cloudberry.pxf.service.bridge.Bridge;
import org.apache.cloudberry.pxf.service.bridge.BridgeFactory;
import org.apache.cloudberry.pxf.service.security.SecurityService;

import java.security.PrivilegedAction;
import java.time.Duration;
import java.time.Instant;

/**
 * Base abstract implementation of the Service class, provides means to execute an operation
 * using provided request context and the identity determined by the security service.
 */
@Slf4j
public abstract class BaseServiceImpl<T> extends PxfErrorReporter<T> {

    protected final MetricsReporter metricsReporter;
    private final String serviceName;
    private final ConfigurationFactory configurationFactory;
    private final BridgeFactory bridgeFactory;
    private final SecurityService securityService;
    private final ActiveRequestRegistry activeRequestRegistry;

    /**
     * Creates a new instance of the service with auto-wired dependencies.
     *
     * @param serviceName           name of the service
     * @param configurationFactory  configuration factory
     * @param bridgeFactory         bridge factory
     * @param securityService       security service
     * @param metricsReporter       metrics reporter service
     * @param activeRequestRegistry registry of in-flight requests
     */
    protected BaseServiceImpl(String serviceName,
                              ConfigurationFactory configurationFactory,
                              BridgeFactory bridgeFactory,
                              SecurityService securityService,
                              MetricsReporter metricsReporter,
                              ActiveRequestRegistry activeRequestRegistry) {
        this.serviceName = serviceName;
        this.configurationFactory = configurationFactory;
        this.bridgeFactory = bridgeFactory;
        this.securityService = securityService;
        this.metricsReporter = metricsReporter;
        this.activeRequestRegistry = activeRequestRegistry;
    }

    /**
     * Executes an action with the identity determined by the PXF security service.
     *
     * @param context request context
     * @param action  action to execute
     * @return operation statistics
     */
    protected OperationStats processData(RequestContext context, PrivilegedAction<OperationResult> action) throws Exception {
        log.debug("{} service is called for resource {} using profile {}",
                serviceName, context.getDataSource(), context.getProfile());

        // initialize the configuration for this request
        Configuration configuration = configurationFactory.
                initConfiguration(
                        context.getConfig(),
                        context.getServerName(),
                        context.getUser(),
                        context.getAdditionalConfigProps());
        context.setConfiguration(configuration);

        Instant startTime = Instant.now();

        // clear any interrupt status left on this pooled worker thread by a
        // previous request that was targeted by pxf_interrupt_backend, so it
        // cannot leak into the request we are about to process
        Thread.interrupted();

        activeRequestRegistry.register(context);
        OperationResult result;
        try {
            // execute processing action with a proper identity
            result = securityService.doAs(context, action);
        } finally {
            activeRequestRegistry.unregister();
        }

        // obtain results after executing the action
        OperationStats stats = result.getStats();
        Exception exception = result.getException();
        String status = (exception == null) ? "Completed" :
                (Utilities.isClientDisconnectException(exception)) ? "Aborted" : "Failed";

        // log action status and stats
        long recordCount = stats.getRecordCount();
        long byteCount = stats.getByteCount();
        long durationMs = Duration.between(startTime, Instant.now()).toMillis();
        double rate = durationMs == 0 ? 0 : (1000.0 * recordCount / durationMs);
        double byteRate = durationMs == 0 ? 0 : (1000.0 * byteCount / durationMs);

        log.info("{} {} operation [{} ms, {} record{}, {} records/sec, {} bytes, {} bytes/sec]{}",
                status,
                stats.getOperation().name().toLowerCase(),
                durationMs,
                recordCount,
                recordCount == 1 ? "" : "s",
                String.format("%.2f", rate),
                byteCount,
                String.format("%.2f", byteRate),
                (exception == null) ? "" : " for " + result.getSourceName());

        // re-throw the exception if the operation failed
        if (exception != null) {
            throw exception;
        }

        // return operation stats
        return stats;
    }

    /**
     * Returns a new Bridge instance based on the current context.
     *
     * @param context request context
     * @return an instance of the bridge to use
     */
    protected Bridge getBridge(RequestContext context) {
        return bridgeFactory.getBridge(context);
    }

    /**
     * Records the bridge the current worker thread is iterating over so that a
     * concurrent `pxf_cancel_backend` can end it. Pass `null` to
     * detach once the bridge is closed.
     *
     * @param bridge the bridge in use, or {@code null} to detach
     */
    protected void attachBridge(Bridge bridge) {
        activeRequestRegistry.attachBridge(bridge);
    }

    /**
     * @return whether the request currently processed by this thread has been
     * asked to cancel via `pxf_cancel_backend`; the read/write loops poll
     * this so they stop between fragments/records.
     */
    protected boolean isCancelled() {
        return activeRequestRegistry.isCurrentCancelled();
    }
}
