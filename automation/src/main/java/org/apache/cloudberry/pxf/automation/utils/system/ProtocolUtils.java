package org.apache.cloudberry.pxf.automation.utils.system;

/***
 * Utility for working with system-wide parameters
 */
public class ProtocolUtils {

    public final static String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    public final static String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    public static final String PROTOCOL_KEY = "PROTOCOL";
    public static final String PXF_TEST_KEEP_DATA = "PXF_TEST_KEEP_DATA";

    public static ProtocolEnum getProtocol() {

        ProtocolEnum result;
        try {
            String protocol = System.getProperty(PROTOCOL_KEY);
            if (protocol == null) {
                protocol = System.getenv(PROTOCOL_KEY);
            }
            if (protocol == null) {
                protocol = ProtocolEnum.HDFS.name();
            }
            result = ProtocolEnum.valueOf(protocol.toUpperCase());
        } catch (Exception e) {
            result = ProtocolEnum.HDFS; // use HDFS as default mode
        }

        return result;
    }

    public static String getSecret() {
        String secret = System.getProperty(AWS_SECRET_ACCESS_KEY);
        return secret != null ? secret : System.getenv(AWS_SECRET_ACCESS_KEY);
    }

    public static String getAccess() {
        String access = System.getProperty(AWS_ACCESS_KEY_ID);
        String result = access != null ? access : System.getenv(AWS_ACCESS_KEY_ID);
        return result;
    }

    public static String getPxfTestKeepData() {
        String keepData = System.getProperty(PXF_TEST_KEEP_DATA);
        return keepData != null ? keepData : System.getenv().getOrDefault(PXF_TEST_KEEP_DATA, "false");
    }


}
