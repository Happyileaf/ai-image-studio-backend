package com.styletransfer.studio.module.image.service;

import com.styletransfer.studio.common.exception.BizException;
import com.styletransfer.studio.common.result.ResultCode;
import com.styletransfer.studio.config.MinioConfig;
import com.styletransfer.studio.infra.storage.MinioStorageService;
import com.styletransfer.studio.module.image.vo.UploadResult;
import com.styletransfer.studio.security.LoginUser;
import com.styletransfer.studio.security.UserContextHolder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传服务：校验 → 上传到 MinIO → 返回预签名预览 URL
 *
 * <p>隐私原则：不提供原图列表/详情/下载接口，仅本服务供 /upload 调用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_WIDTH = 4096;
    private static final int MAX_HEIGHT = 4096;
    private static final int PREVIEW_EXPIRE_MINUTES = 5;

    private static final Tika TIKA = new Tika();

    private final MinioStorageService minioStorageService;
    private final MinioConfig minioConfig;

    @Value("${app.upload.max-size:10MB}")
    private String maxUploadSizeStr;

    private long maxUploadBytes;

    @PostConstruct
    void init() {
        this.maxUploadBytes = DataSize.parse(maxUploadSizeStr).toBytes();
    }

    /**
     * 上传图片到临时原图桶，返回 fileKey 与 5 分钟预签名预览 URL
     */
    public UploadResult upload(MultipartFile file) {
        // 1. 非空校验
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.IMAGE_ILLEGAL, "上传文件为空");
        }

        // 2. 大小校验
        if (file.getSize() > maxUploadBytes) {
            throw new BizException(ResultCode.IMAGE_TOO_LARGE);
        }

        // 3. 扩展名白名单
        String originalFilename = file.getOriginalFilename();
        String ext = resolveExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(ResultCode.IMAGE_TYPE_UNSUPPORTED);
        }

        // 4. Tika 真实 MIME 校验 + 与扩展名一致性
        String detectedMime = detectMime(file, originalFilename);
        if (detectedMime == null || !detectedMime.startsWith("image/")) {
            throw new BizException(ResultCode.IMAGE_ILLEGAL);
        }
        String expectedMime = expectedMime(ext);
        if (!expectedMime.equalsIgnoreCase(detectedMime)) {
            throw new BizException(ResultCode.IMAGE_ILLEGAL);
        }

        // 5. 分辨率校验 ≤4096×4096
        int[] wh = readDimension(file);
        if (wh == null || wh[0] <= 0 || wh[1] <= 0) {
            throw new BizException(ResultCode.IMAGE_ILLEGAL, "无法解析图片尺寸");
        }
        if (wh[0] > MAX_WIDTH || wh[1] > MAX_HEIGHT) {
            throw new BizException(ResultCode.IMAGE_RESOLUTION_TOO_LARGE);
        }

        // 6. 生成 objectKey = temp/{userId}/{date}/{uuid}.{ext}
        LoginUser ctx = UserContextHolder.getRequired();
        Long userId = ctx.userId();
        String date = LocalDate.now().format(DATE_FMT);
        String objectKey = "temp/" + userId + "/" + date + "/" + UUID.randomUUID() + "." + ext;

        // 7. 上传到 images-source-temp 桶
        String bucket = minioConfig.getBuckets().getSourceTemp();
        try (InputStream is = file.getInputStream()) {
            minioStorageService.uploadObject(bucket, objectKey, is, file.getSize(), detectedMime);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ImageService] 上传 MinIO 失败 userId={}, key={}", userId, objectKey, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件上传失败");
        }

        // 8. 生成 5min 预签名 GET URL 作为 previewUrl
        String previewUrl = minioStorageService.presignedGetUrl(bucket, objectKey, PREVIEW_EXPIRE_MINUTES);

        log.info("[ImageService] 图片上传成功 userId={}, key={}, size={}, {}x{}",
                userId, objectKey, file.getSize(), wh[0], wh[1]);

        // 9. 返回
        return UploadResult.builder()
                .fileKey(objectKey)
                .previewUrl(previewUrl)
                .build();
    }

    // ===== 私有方法 =====

    private String detectMime(MultipartFile file, String filename) {
        try (InputStream is = file.getInputStream()) {
            return TIKA.detect(is, filename);
        } catch (Exception e) {
            log.warn("[ImageService] Tika 检测失败 file={}", filename, e);
            return null;
        }
    }

    private String resolveExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String expectedMime(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "";
        };
    }

    /**
     * 读取图片宽高。JDK 自带 ImageIO 不支持 WebP，故先尝试 WebP RIFF 头解析，
     * 其余格式（jpeg/png）走 ImageReader 仅读 header。
     */
    private int[] readDimension(MultipartFile file) {
        int[] webp = tryReadWebpDimension(file);
        if (webp != null) {
            return webp;
        }
        return readDimensionByImageIO(file);
    }

    /**
     * 解析 WebP RIFF 头获取宽高（VP8 / VP8L / VP8X）。非 WebP 或解析失败返回 null。
     */
    private int[] tryReadWebpDimension(MultipartFile file) {
        byte[] header = new byte[30];
        try (InputStream is = file.getInputStream()) {
            if (readN(is, header) < header.length) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        // RIFF....WEBP
        if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
            return null;
        }
        if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
            return null;
        }
        String fourcc = new String(header, 12, 4, StandardCharsets.US_ASCII);
        return switch (fourcc) {
            // VP8（有损）：宽高 14-bit LE，位于 frame tag + start code 之后（offset 26-29）
            case "VP8 " -> new int[]{
                    (le16(header, 26)) & 0x3FFF,
                    (le16(header, 28)) & 0x3FFF
            };
            // VP8L（无损）：4 字节 bit-packed，width-1 / height-1 各 14 位
            case "VP8L" -> {
                int val = (header[21] & 0xFF) | ((header[22] & 0xFF) << 8)
                        | ((header[23] & 0xFF) << 16) | ((header[24] & 0xFF) << 24);
                yield new int[]{(val & 0x3FFF) + 1, ((val >> 14) & 0x3FFF) + 1};
            }
            // VP8X（扩展）：canvas 宽/高各 24-bit LE（offset 24-26 / 27-29），存储的是 size-1
            case "VP8X" -> new int[]{
                    le24(header, 24) + 1,
                    le24(header, 27) + 1
            };
            default -> null;
        };
    }

    private int[] readDimensionByImageIO(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            if (iis == null) {
                return null;
            }
            var readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            log.warn("[ImageService] ImageIO 读取尺寸失败", e);
            return null;
        }
    }

    private int readN(InputStream is, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int r = is.read(buf, total, buf.length - total);
            if (r < 0) {
                break;
            }
            total += r;
        }
        return total;
    }

    private int le16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private int le24(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16);
    }
}
