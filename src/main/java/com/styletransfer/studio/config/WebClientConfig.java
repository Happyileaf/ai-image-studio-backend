package com.styletransfer.studio.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.time.Duration;

/**
 * WebClient 配置（响应式 HTTP 客户端）
 *
 * <p>用途：
 * <ul>
 *   <li>调用第三方 AI 模型 API（风格迁移）</li>
 *   <li>调用 Resend 邮件服务 HTTP API（发送验证码）</li>
 * </ul>
 * <b>注意：仅作 Client，不启动 Netty WebFlux 服务器；服务器仍是 Spring MVC。</b>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "resend")
public class WebClientConfig {

    /** Resend API Key */
    private String apiKey;
    /** 发件人邮箱 */
    private String fromEmail;
    /** 发件人名称 */
    private String fromName;

    /**
     * 通用 WebClient（基础版，调用 AI API 时按需配置 baseUrl）
     */
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                // 响应超时 + 连接超时：默认 60s，可在 AI Provider 中 per-request 覆盖
                .responseTimeout(Duration.ofSeconds(60))
                // Dev 环境：允许自签证书（避免本地测试 SSL 报错；生产去掉）
                .secure(ssl -> {
                    try {
                        ssl.sslContext(SslContextBuilder.forClient()
                                .trustManager(InsecureTrustManagerFactory.INSTANCE).build());
                    } catch (SSLException e) {
                        throw new RuntimeException("Failed to build SSL context for WebClient", e);
                    }
                });

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
