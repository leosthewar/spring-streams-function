package com.leosdev.springstreamsfunction.config;

public final class MonitoringHeaders {
    public static final String MONITORING_ID_CONTEXT = "X-Monitoring-Id-context";
    public static final String MONITORING_ID_HEADER = "X-Monitoring-Id";
    public static final String FUNCTION_NAME_HEADER = "X-Function-Name";
    public static final String SERVICE_NAME_HEADER = "X-Service-Name";
    public static final String EVENT_HEADER = "X-Event";

    private MonitoringHeaders() {}
}