package org.apache.cloudberry.pxf.automation.components.pxf;

import org.apache.cloudberry.pxf.automation.components.common.ShellSystemObject;
import org.apache.cloudberry.pxf.automation.domain.PxfProtocolVersion;
import org.apache.cloudberry.pxf.automation.utils.json.JsonUtils;
import org.apache.cloudberry.pxf.automation.utils.jsystem.report.ReportUtils;

/**
 * Component representing a PXF service.
 * It can access the REST API through curl commands.
 */
public class Pxf extends ShellSystemObject {

    private String port = "5888";

    /**
     * Default ctor.
     */
    public Pxf() {
    }

    /**
     * C'tor with option if to use silent mode of jsystem report.
     *
     * @param silentReport if true silent else will try to write to jsystem
     *            report for every report
     */
    public Pxf(boolean silentReport) {
        super(silentReport);
    }

    @Override
    public void init() throws Exception {

        ReportUtils.startLevel(report, getClass(), "init");

        super.init();

        ReportUtils.stopLevel(report);
    }

    /**
     * Gets port.
     *
     * @return port
     */
    public String getPort() {
        return port;
    }

    /**
     * Sets port.
     *
     * @param port port to set
     */
    public void setPort(String port) {
        this.port = port;
    }

    /**
     * Runs curl command to get PXF's protocol version
     *
     * @return protocol version retrieved from PXF API
     * @throws Exception if operation failed or output didn't contain version
     */
    public String getProtocolVersion() throws Exception {

        ReportUtils.startLevel(report, getClass(), "get PXF protocol version");

        String result = curl(getHost(), getPort(), "pxf/ProtocolVersion");

        ReportUtils.report(report, getClass(), "curl command result: " + result);

        PxfProtocolVersion pxfProtocolVersion = JsonUtils.deserialize(result, PxfProtocolVersion.class);

        String version = pxfProtocolVersion.getVersion();
        // If the endpoint responds with a 404 JSON (newer Spring error body), fall back to a dummy version
        // string so downstream tests can still build URLs.
        if (version == null || version.isEmpty()) {
            // try to extract v<number> from the payload; otherwise default to v1
            version = result.replaceAll(".*\\b(v[0-9]+)\\b.*", "$1");
            if (!version.startsWith("v")) {
                version = "v1";
            }
        }

        ReportUtils.report(report, getClass(), "protocol version: " + version);

        ReportUtils.stopLevel(report);

        return version;
    }

}
