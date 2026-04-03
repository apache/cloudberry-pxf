package listeners;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Retries failed tests up to {@value MAX_RETRIES} times with exponential
 * backoff to handle transient CI failures (e.g. HDFS timeouts on
 * resource-constrained GitHub Actions runners).
 *
 * <p>Delay schedule: 3-8s, 6-16s, 12-32s (capped at 60s).
 *
 * <p>Tests that write data to HDFS without cleanup are excluded from retry
 * because retrying would append duplicate data and cause row-count mismatches.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final int MAX_RETRIES = 3;
    private static final int BASE_MIN_MS = 3000;
    private static final int BASE_MAX_MS = 8000;
    private static final int MAX_DELAY_MS = 60000;

    /** Tests that accumulate data on retry — skip retrying these. */
    private static final Set<String> NO_RETRY_TESTS = new HashSet<>(Arrays.asList(
            "copyFromFileMultiBlockedDataNoCompression",
            "copyFromFileMultiBlockedDataGZip",
            "copyFromFileMultiBlockedDataBZip2"
    ));

    private int retryCount = 0;
    private final Random random = new Random();

    @Override
    public boolean retry(ITestResult result) {
        String methodName = result.getMethod().getMethodName();
        if (NO_RETRY_TESTS.contains(methodName)) {
            System.out.println("[RetryAnalyzer] Skipping retry for " + methodName
                    + " (write-without-cleanup test)");
            return false;
        }
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            int multiplier = 1 << (retryCount - 1); // 1, 2, 4
            int minDelay = Math.min(BASE_MIN_MS * multiplier, MAX_DELAY_MS);
            int maxDelay = Math.min(BASE_MAX_MS * multiplier, MAX_DELAY_MS);
            int delay = minDelay + random.nextInt(maxDelay - minDelay + 1);
            System.out.println("[RetryAnalyzer] Retrying failed test: "
                    + result.getTestClass().getName() + "." + methodName
                    + " after " + delay + "ms delay"
                    + " (attempt " + (retryCount + 1) + "/" + (MAX_RETRIES + 1) + ")");
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }
        return false;
    }
}
