package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.file.StoredFileRef;
import vip.mate.dataagent.service.DatasetFileStorageService;

import java.io.IOException;
import java.util.Set;

/** 平台文件上传入口；只产生内部 StoredFileRef，不接受路径/URL。 */
@RestController
@RequestMapping("/v1/dataset-files")
@RequiredArgsConstructor
@Tag(name = "文件数据集", description = "上传并登记受控文件对象")
public class DataAgentDatasetFileController {
    private final DatasetFileStorageService storageService;
    private final WorkspaceGuard workspaceGuard;

    @PostMapping(consumes = "multipart/form-data")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "上传文件", description = "上传 CSV、JSON、XLSX 或 Parquet 文件并返回受控对象引用")
    public R<StoredFileRef> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("file is required");
        Long workspaceId = workspaceGuard.currentWorkspaceId(); Long userId = workspaceGuard.currentUserId();
        DatasetAccessContext context = new DatasetAccessContext(workspaceId, userId, "upload-" + userId, Set.of());
        return R.ok(storageService.put(context, userId, file.getOriginalFilename(), file.getInputStream(), file.getSize()));
    }
}
