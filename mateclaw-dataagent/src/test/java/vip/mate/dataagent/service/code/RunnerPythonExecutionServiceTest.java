package vip.mate.dataagent.service.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import vip.mate.dataagent.service.code.impl.RunnerPythonExecutionService;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class RunnerPythonExecutionServiceTest {
    @Test void submitsWithoutRequirementsAndMapsResponse() {
        RestTemplate rest = new RestTemplate(); MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("http://runner/v1/tasks")).andExpect(r -> assertEquals(POST, r.getMethod())).andRespond(withSuccess("{\"taskId\":\"t1\",\"status\":\"RUNNING\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        var service = new RunnerPythonExecutionService("http://runner", new ObjectMapper(), rest);
        assertEquals("RUNNING", service.submit(Map.of("taskId", "t1")).get("status"));
        assertThrows(IllegalArgumentException.class, () -> service.submit(Map.of("requirements", "pip install x")));
        server.verify();
    }
    @Test void getsStatusAndCancelsUsingTheValidatedTaskPath() {
        RestTemplate rest = new RestTemplate(); MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("http://runner/v1/tasks/task-1"))
                .andExpect(r -> assertEquals(GET, r.getMethod()))
                .andRespond(withSuccess("{\"taskId\":\"task-1\",\"status\":\"SUCCEEDED\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://runner/v1/tasks/task-1/cancel"))
                .andExpect(r -> assertEquals(POST, r.getMethod()))
                .andRespond(withSuccess("{\"taskId\":\"task-1\",\"status\":\"CANCELLED\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        var service = new RunnerPythonExecutionService("http://runner", new ObjectMapper(), rest);

        assertEquals("SUCCEEDED", service.getStatus("task-1").get("status"));
        assertEquals("CANCELLED", service.cancel("task-1").get("status"));
        server.verify();
    }

    @Test void rejectsUnsafeTaskIdsBeforeMakingAnHttpCall() {
        var service = new RunnerPythonExecutionService("http://runner", new ObjectMapper(), new RestTemplate());
        assertThrows(IllegalArgumentException.class, () -> service.getStatus("task/../../secret"));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(""));
    }

    @Test void mapsRunnerUnavailableToStableServiceError() {
        RestTemplate rest = new RestTemplate(); MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("http://runner/v1/tasks/task-1"))
                .andRespond(withServerError());
        var service = new RunnerPythonExecutionService("http://runner", new ObjectMapper(), rest);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.getStatus("task-1"));
        assertEquals("python runner unavailable", error.getMessage());
        server.verify();
    }
}
