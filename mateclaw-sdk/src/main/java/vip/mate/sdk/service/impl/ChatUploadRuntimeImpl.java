package vip.mate.sdk.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.sdk.service.ChatUploadRuntime;
import vip.mate.workspace.conversation.ConversationService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 聊天附件上传运行时实现
 * <p>
 * 参考 mateclaw-server ChatController 的 upload / readUploadedFile 逻辑，
 * 为宿主应用提供编程式聊天附件上传与读取能力，无需依赖 HTTP 端点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatUploadRuntimeImpl implements ChatUploadRuntime {

    /** 上传文件根目录 */
    private static final String UPLOAD_ROOT_DIR = "data/chat-uploads";

    /** 附件访问 URL 前缀 */
    private static final String FILE_URL_PREFIX = "/api/v1/chat/files/";

    /** 文件名安全字符替换正则 */
    private static final String FILENAME_SANITIZE_REGEX = "[^a-zA-Z0-9._-]";

    /** 文件名安全字符替换值 */
    private static final String FILENAME_SANITIZE_REPLACEMENT = "_";

    /** 默认文件名 */
    private static final String DEFAULT_FILENAME = "file";

    private final ConversationService conversationService;

    @Override
    public ChatUploadResult upload(String conversationId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : DEFAULT_FILENAME;
        String safeFilename = Path.of(originalFilename).getFileName().toString()
                .replaceAll(FILENAME_SANITIZE_REGEX, FILENAME_SANITIZE_REPLACEMENT);
        String storedName = System.currentTimeMillis() + "_" + safeFilename;

        Path uploadRoot = Paths.get(UPLOAD_ROOT_DIR);
        Path conversationDir = uploadRoot.resolve(conversationId);
        try {
            Files.createDirectories(conversationDir);
        } catch (IOException e) {
            throw new RuntimeException("创建上传目录失败: " + conversationDir, e);
        }

        Path target = conversationDir.resolve(storedName);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("文件存储失败: " + target, e);
        }

        log.info("Chat attachment uploaded: conversationId={}, file={}", conversationId, target);

        String url = FILE_URL_PREFIX + conversationId + "/" + storedName;
        String relativePath = uploadRoot.resolve(conversationId).resolve(storedName).toString();

        return new ChatUploadResult(
                conversationId,
                originalFilename,
                storedName,
                url,
                relativePath,
                file.getSize(),
                file.getContentType()
        );
    }

    @Override
    public Resource readUploadedFile(String conversationId, String storedName) {
        Path uploadRoot = Paths.get(UPLOAD_ROOT_DIR).toAbsolutePath().normalize();
        Path conversationDir = uploadRoot.resolve(conversationId).normalize();
        Path filePath = conversationDir.resolve(storedName).normalize();

        // 防路径遍历攻击：确保解析后的路径在会话目录内
        if (!filePath.startsWith(conversationDir)) {
            log.warn("Path traversal detected: conversationId={}, storedName={}", conversationId, storedName);
            return null;
        }

        if (!Files.exists(filePath)) {
            return null;
        }

        return new FileSystemResource(filePath);
    }
}
