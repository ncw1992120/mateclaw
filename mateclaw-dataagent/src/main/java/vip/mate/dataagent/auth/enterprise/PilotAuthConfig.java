package vip.mate.dataagent.auth.enterprise;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 领航认证配置装配
 *
 * @author MateClaw Team
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PilotAuthProperties.class)
public class PilotAuthConfig {
}
