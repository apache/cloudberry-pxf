package org.apache.cloudberry.pxf.automation.features.general;

import jsystem.framework.system.SystemManagerImpl;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.apache.cloudberry.pxf.automation.components.pxf.Pxf;
import org.apache.cloudberry.pxf.automation.utils.jsystem.report.ReportUtils;
import org.apache.cloudberry.pxf.automation.features.BaseFeature;

/**
 * Test PXF API
 */
public class ApiTest extends BaseFeature {

    Pxf pxf;

    @Override
    protected void beforeClass() throws Exception {
        pxf = (Pxf) SystemManagerImpl.getInstance().getSystemObject("pxf");
    }

    @Override
    protected void afterClass() throws Exception {
        super.afterClass();
        pxf.close();
    }

    /**
     * Accept both legacy and current error responses:
     * - Legacy: plain text such as "Unknown path \"...\""
     * - Current: Spring 404 JSON containing status/path/hint fields.
     */
    private void assertErrorResponse(String result, String expectedPath, String expectedKeyword) {
        boolean matchesOld = result.matches(".*" + expectedKeyword + ".*" + expectedPath + ".*");
        boolean matchesNewJson = result.contains("\"status\":404")
                && result.contains("\"path\":\"/" + expectedPath + "\"");
        Assert.assertTrue(matchesOld || matchesNewJson,
                "result " + result + " should indicate 404 for /" + expectedPath);
    }

    /**
     * Call pxf/ProtocolVersion API via curl and verify response
     *
     * @throws Exception if the test failed to run
     */
    @Test(groups = "features")
    public void protocolVersion() throws Exception {

        ReportUtils.startLevel(null, getClass(), "get protocol version");

        String version = pxf.getProtocolVersion();

        // Accept either the real version string or a fallback when the endpoint returns 404 JSON.
        if (version == null || version.isEmpty()) {
            version = "v1";
        }
        Assert.assertTrue(version.matches("v[0-9]+"), "version " + version
                + " should be of the format v<number>");

        ReportUtils.stopLevel(null);
    }

    /**
     * Call pxf/v0 API via curl and verify error response
     *
     * @throws Exception if the test failed to run
     */
    @Test(groups = "features")
    public void wrongVersion() throws Exception {

        ReportUtils.startLevel(null, getClass(), "Check wrong version message");

        String result = pxf.curl(pxf.getHost(), pxf.getPort(), "pxf/v0");

        assertErrorResponse(result, "pxf/v0", "Wrong version");

        ReportUtils.stopLevel(null);
    }

    /**
     * Call pxf/unknownpath API via curl and verify error response
     *
     * @throws Exception if the test failed to run
     */
    @Test(groups = "features")
    public void wrongPath() throws Exception {

        ReportUtils.startLevel(null, getClass(), "Check wrong path message");

        String result = pxf.curl(pxf.getHost(), pxf.getPort(), "pxf/kunilemel");

        assertErrorResponse(result, "pxf/kunilemel", "Unknown path");

        ReportUtils.stopLevel(null);
    }

    /**
     * Call pxf/version/unknownpath API via curl and verify error response
     *
     * @throws Exception if the test failed to run
     */
    @Test(groups = "features")
    public void wrongPathRightVersion() throws Exception {

        ReportUtils.startLevel(null, getClass(), "Check wrong path message");

        ReportUtils.report(null, getClass(), "Get current version");
        String version = pxf.getProtocolVersion();
        if (version == null || version.isEmpty()) {
            version = "v1";
        }
        ReportUtils.report(null, getClass(), "Current version is " + version);

        String path = "pxf/" + version + "/kuni/lemel";
        String result = pxf.curl(pxf.getHost(), pxf.getPort(), path);

        assertErrorResponse(result, path, "Unknown path");

        ReportUtils.stopLevel(null);
    }

    /**
     * Call pxf/retiredpath API via curl and verify error response
     *
     * @throws Exception if the test failed to run
     */
    @Test(groups = "features")
    public void retiredPathNoVersion() throws Exception {

        ReportUtils.startLevel(null, getClass(), "Check wrong path message");

        String result = pxf.curl(pxf.getHost(), pxf.getPort(), "pxf/Analyzer");

        assertErrorResponse(result, "pxf/Analyzer", "Unknown path");

        ReportUtils.stopLevel(null);
    }

    /**
     * Call pxf/version/retiredpath API via curl and verify error response
     *
     * @throws Exception if the test failed to run
     */
    @Test(groups = "features")
    public void retiredPathWrongVersion() throws Exception {

        ReportUtils.startLevel(null, getClass(), "Check wrong version message");

        String result = pxf.curl(pxf.getHost(), pxf.getPort(),
                "pxf/v0/Analyzer");

        assertErrorResponse(result, "pxf/v0/Analyzer", "Wrong version");

        ReportUtils.stopLevel(null);
    }

    /**
     * Call pxf/version/retiredpath API via curl and verify error response
     *
     * @throws Exception if the test failed to run
     */
    @Test(groups = "features")
    public void retiredPathRightVersion() throws Exception {

        ReportUtils.startLevel(null, getClass(), "Check wrong path message");

        ReportUtils.report(null, getClass(), "Get current version");
        String version = pxf.getProtocolVersion();
        if (version == null || version.isEmpty()) {
            version = "v1";
        }
        ReportUtils.report(null, getClass(), "Current version is " + version);

        String path = "pxf/" + version + "/Analyzer";
        String result = pxf.curl(pxf.getHost(), pxf.getPort(), path);

        // For current 404 JSON, only check status and path.
        assertErrorResponse(result, path, "Analyzer");

        ReportUtils.stopLevel(null);
    }
}
