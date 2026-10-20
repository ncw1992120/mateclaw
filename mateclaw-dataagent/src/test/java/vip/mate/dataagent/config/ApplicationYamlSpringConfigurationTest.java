package vip.mate.dataagent.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Import;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationYamlSpringConfigurationTest {

    @Test
    void bindsSpringOwnedSettingsAtSpringRoot() throws IOException {
        ConfigurableEnvironment environment = applicationEnvironment();
        Binder binder = Binder.get(environment);

        ElasticsearchProperties elasticsearch = binder.bind("spring.elasticsearch", ElasticsearchProperties.class).get();
        HikariConfig hikari = binder.bind("spring.datasource.hikari", HikariConfig.class).get();
        MultipartProperties multipart = binder.bind("spring.servlet.multipart", MultipartProperties.class).get();
        FlywayProperties flyway = binder.bind("spring.flyway", FlywayProperties.class).get();

        assertEquals("http://es.service:80", elasticsearch.getUris());
        assertEquals("elastic", elasticsearch.getUsername());
        assertEquals(20, hikari.getMaximumPoolSize());
        assertEquals(30_000, hikari.getConnectionTimeout());
        assertEquals(DataSize.ofMegabytes(100), multipart.getMaxFileSize());
        assertEquals(DataSize.ofMegabytes(200), multipart.getMaxRequestSize());
        assertTrue(flyway.isEnabled());
        assertEquals("199", flyway.getBaselineVersion());
        assertTrue(flyway.isOutOfOrder());
        assertEquals("configure-in-admin-ui", environment.getProperty("spring.ai.dashscope.api-key"));
        assertEquals("pgsql", environment.getProperty("spring.profiles.default"));
    }

    @Test
    void startsElasticsearchConfigurationFromApplicationYamlWithoutConnecting() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ElasticsearchTestConfiguration.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertArrayEquals(new String[]{"pgsql"}, context.getEnvironment().getDefaultProfiles());
                    assertTrue(context.getEnvironment().acceptsProfiles(Profiles.of("pgsql")));
                    assertNotNull(context.getBean(ElasticsearchClient.class));
                    assertNotNull(context.getBean(ElasticsearchStartupDiagnostics.class));
                });
    }

    @Test
    void doesNotKeepMisplacedOrObsoleteSettings() throws IOException {
        ConfigurableEnvironment environment = applicationEnvironment();

        assertNull(environment.getProperty("mateclaw.elasticsearch.uris"));
        assertNull(environment.getProperty("mateclaw.profiles.default"));
        assertNull(environment.getProperty("mateclaw.servlet.multipart.max-file-size"));
        assertNull(environment.getProperty("mateclaw.datasource.hikari.maximum-pool-size"));
        assertNull(environment.getProperty("mateclaw.flyway.enabled"));
        assertNull(environment.getProperty("mateclaw.ai.dashscope.api-key"));
        assertNull(environment.getProperty("mateclaw.dataset.http.allow-insecure-test-endpoint"));
        assertNull(environment.getProperty("mateclaw.dataset.http.allow-tls-test-endpoint"));
    }

    private static ConfigurableEnvironment applicationEnvironment() throws IOException {
        ConfigurableEnvironment environment = new org.springframework.core.env.StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        List<String> sourceNames = StreamSupport.stream(sources.spliterator(), false)
                .map(source -> source.getName())
                .toList();
        sourceNames.forEach(sources::remove);
        sources.addLast(applicationYamlPropertySource());
        return environment;
    }

    private static org.springframework.core.env.PropertySource<?> applicationYamlPropertySource() throws IOException {
        return new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .get(0);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ElasticsearchProperties.class)
    @Import({ElasticsearchConfig.class, ElasticsearchStartupDiagnostics.class})
    static class ElasticsearchTestConfiguration {
    }
}
