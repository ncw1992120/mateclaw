package vip.mate.dataagent.service.code.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import vip.mate.dataagent.service.code.PythonExecutionService;

import java.util.Map;

/** Python Runner 控制面客户端；不接收用户 requirements 或连接凭据。 */
@Service
public class RunnerPythonExecutionService implements PythonExecutionService {
    private final RestTemplate rest; private final ObjectMapper mapper; private final String baseUrl;
    @Autowired
    public RunnerPythonExecutionService(@Value("${mateclaw.runner.url:http://python-runner:8080}") String baseUrl, ObjectMapper mapper) { this(baseUrl, mapper, new RestTemplate()); }
    public RunnerPythonExecutionService(String baseUrl, ObjectMapper mapper, RestTemplate rest) { this.baseUrl=baseUrl.replaceAll("/$",""); this.mapper=mapper; this.rest=rest; }
    @Override public Map<String,Object> submit(Map<String,Object> request) { if(request==null||request.containsKey("requirements")) throw new IllegalArgumentException("requirements is not supported"); return call(HttpMethod.POST,"/v1/tasks",request); }
    @Override public Map<String,Object> getStatus(String taskId) { return call(HttpMethod.GET,"/v1/tasks/"+safe(taskId),null); }
    @Override public Map<String,Object> cancel(String taskId) { return call(HttpMethod.POST,"/v1/tasks/"+safe(taskId)+"/cancel",Map.of()); }
    private Map<String,Object> call(HttpMethod method,String path,Object body){try{HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON); ResponseEntity<String> response=rest.exchange(baseUrl+path,method,new HttpEntity<>(body,h),String.class); return mapper.readValue(response.getBody(),new TypeReference<>(){});}catch(RestClientException e){throw new IllegalStateException("python runner unavailable",e);}catch(Exception e){throw new IllegalStateException("invalid runner response",e);}}
    private String safe(String id){if(id==null||id.isBlank()||!id.matches("[A-Za-z0-9._-]{1,128}"))throw new IllegalArgumentException("invalid task id");return id;}
}
