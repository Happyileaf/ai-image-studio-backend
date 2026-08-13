package com.styletransfer.studio.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置 + 自定义属性绑定
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /** MinIO Server Endpoint（例如 http://localhost:9000） */
    private String endpoint;

    private String accessKey;
    private String secretKey;

    /** 预签名 URL 有效期（秒）：结果图 1 小时，原图预览 5 分钟 */
    private long presignedExpireSeconds = 3600L;

    private Buckets buckets = new Buckets();

    @Data
    public static class Buckets {
        private String sourceTemp = "images-source-temp";
        private String result = "images-result";
        private String style = "styles-cover";
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
