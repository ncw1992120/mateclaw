package vip.mate.dataagent.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.service.code.*;
import vip.mate.dataagent.objectref.ObjectRefService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.*;

/** Runner 内部读取接口：客户端只能提交别名、字段和过滤条件，不能指定 datasetId。 */
@RestController
@RequestMapping("/internal/v1/script-tasks")
@RequiredArgsConstructor
public class ScriptDatasetReadController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ScriptDatasetReadController.class);
    private final ScriptTaskInputRegistry registry;
    private final ScriptDatasetReadTokenService tokens;
    private final List<DatasetSourceAdapter> adapters;
    private final ObjectRefService objectRefs;
    private final ScriptDatasetReadPolicy readPolicy;

    @PostMapping("/{taskId}/datasets/read")
    public R<DatasetBatch> read(@PathVariable String taskId, @RequestHeader("Authorization") String authorization,
                                HttpServletRequest request, @RequestBody ReadBody body) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException("Bearer token required");
        var claims=tokens.verify(authorization.substring(7)); if(!taskId.equals(claims.taskId())) throw new IllegalArgumentException("token task mismatch");
        List<DatasetFilter> filters=body.filters()==null?List.of():body.filters().stream().map(f->new DatasetFilter(f.field(),f.role(),f.operator(),f.value())).toList();
        long requestBytes=request.getContentLengthLong() < 0 ? 0 : request.getContentLengthLong();
        try {
            // 策略校验必须在 Adapter 之前：超限请求不允许触达数据源
            readPolicy.validate(filters, requestBytes);
        } catch (IllegalArgumentException e) {
            log.warn("[script-read] policy rejected taskId={} inputName={} limit={}", taskId, body.inputName(), e.getMessage());
            throw e;
        }
        DatasetInputDescriptor descriptor=registry.require(taskId,body.inputName());
        DatasetSourceAdapter adapter=adapters.stream().filter(a->a.supports(descriptor.sourceType())).findFirst().orElseThrow(()->new IllegalArgumentException("no adapter"));
        DatasetAccessContext context=new DatasetAccessContext(claims.workspaceId(),0L,taskId,Set.of(descriptor.datasetId()));
        return R.ok(adapter.read(context,new DatasetReadRequest(descriptor.datasetId(),body.inputName(),body.columns(),filters,body.limit(),body.offset(),body.parameters())));
    }

    /** Runner 大结果上传接口：令牌绑定 task/workspace，DataAgent 负责写入受控 ObjectRef。 */
    @PostMapping("/{taskId}/result")
    public R<vip.mate.dataagent.dataset.ObjectRef> uploadResult(
            @PathVariable String taskId,
            @RequestHeader("Authorization") String authorization,
            HttpServletRequest request) throws java.io.IOException {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException("Bearer token required");
        var claims = tokens.verify(authorization.substring(7));
        if (!taskId.equals(claims.taskId())) throw new IllegalArgumentException("token task mismatch");
        long maxBytes = 100L * 1024 * 1024;
        if (request.getContentLengthLong() > maxBytes) throw new IllegalArgumentException("result too large");
        var context = new DatasetAccessContext(claims.workspaceId(), 0L, taskId, Set.of());
        return R.ok(objectRefs.put(context, "parquet", new LimitedInputStream(request.getInputStream(), maxBytes)));
    }

    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private final long max;
        private long count;
        private LimitedInputStream(java.io.InputStream delegate, long max) { super(delegate); this.max = max; }
        @Override public int read() throws java.io.IOException { int value = super.read(); if (value >= 0 && ++count > max) throw new java.io.IOException("result too large"); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws java.io.IOException { int read = super.read(bytes, offset, length); if (read > 0 && (count += read) > max) throw new java.io.IOException("result too large"); return read; }
    }
    public record ReadBody(String inputName,List<String> columns,List<FilterBody> filters,Integer limit,Integer offset,Map<String,Object> parameters){}
    public record FilterBody(String field,String role,String operator,Object value){}
}
