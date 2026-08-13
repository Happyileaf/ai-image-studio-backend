package com.styletransfer.studio.module.email.infra.mail;

import com.styletransfer.studio.config.WebClientConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Resend 邮件服务 Provider
 *
 * <p>调用 https://api.resend.com/emails 发送邮件。dev 环境若未配置 api-key，仅记日志跳过实际发送
 * （验证码已写入 Redis 可通过日志获取，便于本地调试）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResendProvider {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final WebClient webClient;
    private final WebClientConfig webClientConfig;

    /**
     * 发送验证码邮件
     *
     * @param toEmail  收件人邮箱
     * @param code     验证码
     * @param purpose  用途（REGISTER / RESET_PASSWORD）
     */
    public void sendVerifyCode(String toEmail, String code, String purpose) {
        String apiKey = webClientConfig.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            log.warn("[Resend] 未配置 resend.api-key，跳过实际发送。收件人={} 验证码={} 用途={}", toEmail, code, purpose);
            return;
        }

        String from = String.format("%s <%s>", webClientConfig.getFromName(), webClientConfig.getFromEmail());
        String subject = "【AI 风格迁移】验证码";
        String html = buildVerifyCodeHtml(code, purpose);

        Map<String, Object> body = Map.of(
                "from", from,
                "to", List.of(toEmail),
                "subject", subject,
                "html", html
        );

        try {
            webClient.post()
                    .uri(RESEND_API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            log.info("[Resend] 验证码邮件发送成功 to={} purpose={}", toEmail, purpose);
        } catch (Exception e) {
            // 邮件发送失败不阻断流程：验证码已写入 Redis，用户可重试
            log.error("[Resend] 验证码邮件发送失败 to={} purpose={}", toEmail, purpose, e);
        }
    }

    private String buildVerifyCodeHtml(String code, String purpose) {
        String action = "REGISTER".equals(purpose) ? "注册" : "重置密码";
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px;">
                  <h2 style="color:#333;">AI 风格迁移工作台</h2>
                  <p>您好，您正在进行<strong>%s</strong>操作，验证码为：</p>
                  <div style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#2563eb;
                              background:#f5f5f5;padding:16px;text-align:center;border-radius:8px;">%s</div>
                  <p style="color:#666;font-size:12px;">验证码 10 分钟内有效，请勿泄露给他人。</p>
                </div>
                """.formatted(action, code);
    }
}
