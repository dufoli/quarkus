package io.quarkus.test.common.http;

import java.util.Collections;
import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;

/**
 *
 */
public class TestHTTPConfigSourceProvider implements ConfigSourceProvider {

    static final String TEST_URL_VALUE = "http://${quarkus.http.host:localhost}:${quarkus.http.test-port:8081}${quarkus.servlet.context-path:}";
    static final String TEST_URL_KEY = "test.url";

    public Iterable<ConfigSource> getConfigSources(final ClassLoader forClassLoader) {
        return Collections.singletonList(new ConfigSource() {
            public Map<String, String> getProperties() {
                return Collections.singletonMap(TEST_URL_KEY, TEST_URL_VALUE);
            }

            public String getValue(final String propertyName) {
                return propertyName.equals(TEST_URL_KEY) ? TEST_URL_VALUE : null;
            }

            public String getName() {
                return "test.url provider";
            }

            public int getOrdinal() {
                return Integer.MIN_VALUE + 1000;
            }
        });
    }
}
