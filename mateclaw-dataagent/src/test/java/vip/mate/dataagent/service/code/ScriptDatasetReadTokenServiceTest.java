package vip.mate.dataagent.service.code;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptDatasetReadTokenServiceTest {
    @Test void issuesAndVerifiesScopedToken() {
        var service = new ScriptDatasetReadTokenService("unit-secret");
        String token = service.issue("task-1", 7L, 60);
        var claims = service.verify(token);
        assertEquals("task-1", claims.taskId()); assertEquals(7L, claims.workspaceId());
        assertThrows(IllegalArgumentException.class, () -> service.verify(token + "x"));
    }

    @Test void rejectsExpiredToken() {
        var service = new ScriptDatasetReadTokenService("unit-secret");
        String token = service.issue("task-1", 7L, -1);
        assertThrows(IllegalArgumentException.class, () -> service.verify(token));
    }
}
