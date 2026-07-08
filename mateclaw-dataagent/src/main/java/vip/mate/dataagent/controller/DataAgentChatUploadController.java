package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.common.result.R;
import vip.mate.dataagent.dto.ChatUploadResponse;
import vip.mate.sdk.service.ChatUploadRuntime;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 聊天附件上传控制器
 */
@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
@Tag(name = "DataAgent 附件", description = "数据分析 Agent 聊天附件上传与读取接口")
public class DataAgentChatUploadController {

    private final ChatUploadRuntime chatUploadRuntime;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传聊天附件", description = "上传聊天附件文件，文件存储到会话对应的目录下")
    public R<ChatUploadResponse> upload(
            @RequestParam String conversationId,
            @RequestPart("file") MultipartFile file) {
        ChatUploadRuntime.ChatUploadResult result = chatUploadRuntime.upload(conversationId, file);

        ChatUploadResponse response = new ChatUploadResponse();
        response.setConversationId(result.getConversationId());
        response.setFileName(result.getFileName());
        response.setStoredName(result.getStoredName());
        response.setUrl(result.getUrl());
        response.setSize(result.getSize());
        response.setContentType(result.getContentType());
        return R.ok(response);
    }

    @GetMapping("/files/{conversationId}/{storedName:.+}")
    @Operation(summary = "读取聊天附件", description = "根据会话 ID 和存储名称读取已上传的附件")
    public ResponseEntity<Resource> readUploadedFile(
            @PathVariable String conversationId,
            @PathVariable String storedName) throws IOException {
        Resource resource = chatUploadRuntime.readUploadedFile(conversationId, storedName);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(Path.of(resource.getURI()));
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (contentType != null) {
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
            }
        }

        String encodedFilename = URLEncoder.encode(resource.getFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }
}
