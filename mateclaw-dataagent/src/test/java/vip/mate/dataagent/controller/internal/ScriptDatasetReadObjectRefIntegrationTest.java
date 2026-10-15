package vip.mate.dataagent.controller.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetBatch;
import vip.mate.dataagent.dataset.ObjectRef;
import vip.mate.dataagent.objectref.DatasetBatchCodec;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.objectref.LocalObjectRefService;
import vip.mate.dataagent.service.code.ScriptDatasetReadTokenService;
import vip.mate.dataagent.service.code.ScriptTaskInputRegistry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the HTTP result-upload boundary with filesystem-backed ObjectRef storage. */
class ScriptDatasetReadObjectRefIntegrationTest {
    @TempDir Path storageRoot;

    @Test
    void uploadsParquetOverRunnerBoundaryAndReadsItBackFromLocalStorage() throws Exception {
        DatasetAccessContext context = new DatasetAccessContext(7L, 0L, "task-upload", Set.of());
        CapturingRefs source = new CapturingRefs();
        DatasetBatch batch = new DatasetBatch(List.of(
                Map.of("id", 1L, "value", "one"),
                Map.of("id", 2L, "value", "two")), null, 2, true);
        DatasetBatchCodec.writeParquet(context, batch, source);

        ObjectRefService refs = new LocalObjectRefService(storageRoot, 3600);
        ScriptDatasetReadTokenService tokens = new ScriptDatasetReadTokenService("integration-secret");
        ScriptDatasetReadController controller = new ScriptDatasetReadController(
                new ScriptTaskInputRegistry(), tokens, List.of(), refs, new vip.mate.dataagent.service.code.ScriptDatasetReadPolicy());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        String response = mvc.perform(post("/internal/v1/script-tasks/task-upload/result")
                        .header("Authorization", "Bearer " + tokens.issue("task-upload", 7L, 60))
                        .contentType(MediaType.parseMediaType("application/vnd.apache.parquet"))
                        .content(source.bytes))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> envelope = new ObjectMapper().readValue(response, new TypeReference<>() {});
        ObjectRef reference = new ObjectMapper().convertValue(envelope.get("data"), ObjectRef.class);
        assertNotNull(reference.objectId());
        assertEquals("parquet", reference.format());
        assertEquals(batch.rows(), DatasetBatchCodec.readRows(context, reference, refs, 10));
    }

    private static final class CapturingRefs implements ObjectRefService {
        private byte[] bytes;

        @Override public ObjectRef put(DatasetAccessContext context, String format, InputStream input) {
            try { bytes = input.readAllBytes(); }
            catch (IOException e) { throw new IllegalStateException(e); }
            return new ObjectRef("captured", context.workspaceId(), context.taskId(), format, "sha256:test", 0L);
        }

        @Override public InputStream open(DatasetAccessContext context, ObjectRef reference) {
            return new ByteArrayInputStream(bytes);
        }

        @Override public void expire(ObjectRef reference) { }
    }
}
