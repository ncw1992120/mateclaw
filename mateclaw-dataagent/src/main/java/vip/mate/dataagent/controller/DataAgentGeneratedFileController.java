package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.tool.document.GeneratedFileCache;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 工具生成文件下载控制器
 * <p>
 * 对接 mateclaw-server 的 {@link GeneratedFileCache}，
 * 暴露 {@code /v1/files/generated/{id}} 接口供前端下载工具生成的文件。
 * 端点无需认证，URL 中的 UUID 即为访问凭证，条目在 TTL 后自动过期。
 */
@RestController
@RequestMapping("/v1/files/generated")
@RequiredArgsConstructor
@Tag(name = "DataAgent 生成文件", description = "工具生成文件下载接口")
public class DataAgentGeneratedFileController {

    private final GeneratedFileCache cache;

    @Operation(summary = "根据 ID 下载工具生成的文件", description = "UUID 为访问凭证，无需额外认证，文件在 TTL 后自动过期")
    @GetMapping("/{id}")
    public ResponseEntity<?> download(@PathVariable String id) {
        return cache.get(id)
                .<ResponseEntity<?>>map(entry -> {
                    String encodedName = URLEncoder.encode(entry.filename(), StandardCharsets.UTF_8)
                            .replace("+", "%20");
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType(entry.mimeType()));
                    String disposition = entry.mimeType() != null && entry.mimeType().startsWith("image/")
                            ? "inline"
                            : "attachment";
                    headers.add(HttpHeaders.CONTENT_DISPOSITION,
                            disposition + "; filename=\"" + sanitizeAscii(entry.filename())
                                    + "\"; filename*=UTF-8''" + encodedName);
                    headers.setContentLength(entry.bytes().length);
                    return ResponseEntity.ok().headers(headers).body(entry.bytes());
                })
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("error", "File not found or expired")));
    }

    private String sanitizeAscii(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            sb.append(c < 0x20 || c >= 0x7F || c == '"' || c == '\\' ? '_' : c);
        }
        return sb.toString();
    }
}
