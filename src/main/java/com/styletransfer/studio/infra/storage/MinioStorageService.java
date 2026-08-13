package com.styletransfer.studio.infra.storage;

import com.styletransfer.studio.config.MinioConfig;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储封装：建桶 / 上传 / 删除 / 预签名 URL
 *
 * <p>桶名来源于 {@link MinioConfig#getBuckets()}（绑定 minio.buckets 配置）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 启动时确保三个业务桶存在
     */
    @PostConstruct
    void initBuckets() {
        MinioConfig.Buckets buckets = minioConfig.getBuckets();
        ensureBucket(buckets.getSourceTemp());
        ensureBucket(buckets.getResult());
        ensureBucket(buckets.getStyle());
    }

    /**
     * 确保桶存在，幂等（已存在则忽略）
     */
    public void ensureBucket(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("[MinioStorage] 创建桶: {}", bucketName);
            }
        } catch (ErrorResponseException e) {
            // 桶已存在等错误忽略，保持幂等
            log.warn("[MinioStorage] ensureBucket 忽略已存在异常 bucket={}", bucketName);
        } catch (Exception e) {
            log.error("[MinioStorage] ensureBucket 失败 bucket={}", bucketName, e);
            throw new RuntimeException("初始化 MinIO 桶失败: " + bucketName, e);
        }
    }

    /**
     * 上传对象，返回 objectKey
     */
    public String uploadObject(String bucketName, String objectKey, InputStream is,
                               long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(is, size, -1)
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (Exception e) {
            log.error("[MinioStorage] uploadObject 失败 bucket={}, key={}", bucketName, objectKey, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 获取对象输入流（用于 ZIP 流式下载，调用方负责关闭返回的 InputStream）
     */
    public InputStream getObjectStream(String bucketName, String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("[MinioStorage] getObjectStream 失败 bucket={}, key={}", bucketName, objectKey, e);
            throw new RuntimeException("获取对象流失败", e);
        }
    }

    /**
     * 幂等删除对象（不存在则忽略）
     */
    public void deleteObject(String bucketName, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            // MinIO removeObject 本身对不存在的对象返回成功；此处兜底忽略异常
            log.warn("[MinioStorage] deleteObject 忽略异常 bucket={}, key={}: {}",
                    bucketName, objectKey, e.getMessage());
        }
    }

    /**
     * 生成预签名 GET URL
     *
     * @param expireMinutes 有效期（分钟）
     */
    public String presignedGetUrl(String bucketName, String objectKey, int expireMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectKey)
                    .expiry(expireMinutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("[MinioStorage] presignedGetUrl 失败 bucket={}, key={}", bucketName, objectKey, e);
            throw new RuntimeException("生成预签名 URL 失败", e);
        }
    }
}
