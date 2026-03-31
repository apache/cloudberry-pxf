package listeners;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Retries failed tests up to {@value MAX_RETRIES} time(s) to handle
 * transient CI failures (e.g. HDFS multi-block write timeouts on
 * resource-constrained GitHub Actions runners).
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final int MAX_RETRIES = 1;
    private int retryCount = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            System.out.println("[RetryAnalyzer] Retrying failed test: "
                    + result.getTestClass().getName() + "."
                    + result.getMethod().getMethodName()
                    + " (attempt " + (retryCount + 1) + ")");
            return true;
        }
        return false;
    }
}
