package vip.mate.dataagent.auth.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 传输加密配置装配
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TransportCryptoProperties.class)
public class TransportCryptoConfig {
}