package listeners;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Annotation transformer that attaches {@link RetryAnalyzer} to every
 * test method that does not already have a retry analyzer configured.
 * <p>
 * Register this listener in {@code @Listeners} on the base test class
 * so all automation tests automatically get retry-on-failure behaviour.
 */
public class RetryListener implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation, Class testClass,
                          Constructor testConstructor, Method testMethod) {
        // TestNG 6.x API: getRetryAnalyzer() returns Class
        Class<?> existing = annotation.getRetryAnalyzer();
        if (existing == null) {
            annotation.setRetryAnalyzer(RetryAnalyzer.class);
        }
    }
}
