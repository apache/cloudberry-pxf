package listeners;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.util.Random;

/**
 * Retries failed tests up to {@value MAX_RETRIES} times with exponential
 * backoff to handle transient CI failures (e.g. HDFS multi-block write
 * timeouts on resource-constrained GitHub Actions runners).
 *
 * <p>Delay schedule: 3-8s, 6-16s, 12-32s (capped at 60s).
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final int MAX_RETRIES = 3;
    private static final int BASE_MIN_MS = 3000;
    private static final int BASE_MAX_MS = 8000;
    private static final int MAX_DELAY_MS = 60000;

    private int retryCount = 0;
    private final Random random = new Random();

    @Override
    public boolean retry(ITestResult result) {
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            int multiplier = 1 << (retryCount - 1); // 1, 2, 4
            int minDelay = Math.min(BASE_MIN_MS * multiplier, MAX_DELAY_MS);
            int maxDelay = Math.min(BASE_MAX_MS * multiplier, MAX_DELAY_MS);
            int delay = minDelay + random.nextInt(maxDelay - minDelay + 1);
            System.out.println("[RetryAnalyzer] Retrying failed test: "
                    + result.getTestClass().getName() + "."
                    + result.getMethod().getMethodName()
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
