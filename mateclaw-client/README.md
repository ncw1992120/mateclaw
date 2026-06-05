# MateClaw Client SDK 使用手册

## 1. 概述

MateClaw Client SDK 是一个 HTTP 客户端库，用于集成调用 MateClaw Server 的 REST API。它封装了所有 Controller 层对外提供的 HTTP 接口，可作为 SDK 提供给业务应用进行集成。

**模型类列表（共 80+ 个）：**

`Agent`, `AgentCapabilities`, `AgentToolBinding`, `AgentSkillBinding`, `AgentProviderPreference`, `Template`, `Workspace`, `WorkspaceWithRole`, `WorkspaceAccess`, `WorkspaceMember`, `WorkspaceFile`, `Conversation`, `ConversationVO`, `Message`, `MessageContentPart`, `TokenUsageSummary`, `ChatRequest`, `Channel`, `ChannelSession`, `CronJob`, `CronJobDTO`, `DeliveryConfig`, `CronJobRun`, `ActiveCronRun`, `UsageDaily`, `Datasource`, `Goal`, `GoalEvent`, `GoalEvaluationResult`, `GoalCreateRequest`, `GoalUpdateRequest`, `ModelConfig`, `ModelProvider`, `ModelInfo`, `ProviderInfo`, `ModelSlotConfig`, `ActiveModelsInfo`, `TestResult`, `DiscoverResult`, `EnableResult`, `Tool`, `AvailableTool`, `McpServer`, `McpToolDescriptor`, `Skill`, `ResolvedSkill`, `InstallResult`, `Workflow`, `WorkflowRun`, `WorkflowRunStep`, `WorkflowRunPause`, `WikiKnowledgeBase`, `WikiPage`, `WikiRawMaterial`, `WikiTransformation`, `WikiTransformationRun`, `WikiRelation`, `WikiHotCache`, `WikiChunk`, `Trigger`, `TriggerEvent`, `MemoryRecall`, `DreamReport`, `Fact`, `User`, `LoginRequest`, `LoginResponse`, `AuditEvent`, `ToolApproval`, `SecurityFinding`, `AcpEndpoint`, `SystemSettings`, `Plugin`, `PluginInfo`, `Plan`, `SubPlan`, `Hook`, `HookRun`, `AsyncTaskInfo`

## 2. 快速开始

### 2.1 添加 Maven 依赖

```xml
<dependency>
    <groupId>vip.mate</groupId>
    <artifactId>mateclaw-client</artifactId>
    <version>${mateclaw.version}</version>
</dependency>
```

### 2.2 配置属性

**YAML 格式（推荐用户名密码方式）：**

```yaml
mateclaw:
  client:
    base-url: http://localhost:8080
    username: admin
    password: your-password
    connect-timeout: 5000
    read-timeout: 30000
    default-workspace-id: 1
```

**YAML 格式（PAT 静态 Token 方式）：**

```yaml
mateclaw:
  client:
    base-url: http://localhost:8080
    token: mc_your-personal-access-token
```

**Properties 格式：**

```properties
mateclaw.client.base-url=http://localhost:8080
mateclaw.client.username=admin
mateclaw.client.password=your-password
mateclaw.client.connect-timeout=5000
mateclaw.client.read-timeout=30000
mateclaw.client.default-workspace-id=1
```

### 2.3 注入使用

```java
@Service
public class MyService {

    @Autowired
    private MateClawClient client;

    public void demo() {
        // 直接使用 Agent 模型类，无需 Map 转换
        R<List<Agent>> result = client.agent().list(null);
        if (result.isSuccess()) {
            for (Agent agent : result.getData()) {
                System.out.println(agent.getName() + " - " + agent.getAgentType());
            }
        }
    }
}
```

## 3. 配置说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `mateclaw.client.base-url` | String | `http://localhost:8080` | MateClaw Server 基础地址 |
| `mateclaw.client.username` | String | 无 | 用户名（与 password 配合使用，优先级高于 token） |
| `mateclaw.client.password` | String | 无 | 密码（与 username 配合使用） |
| `mateclaw.client.token` | String | 无 | 认证 Token（JWT 或 PAT，优先级低于 username/password） |
| `mateclaw.client.connect-timeout` | int | `5000` | 连接超时时间（毫秒） |
| `mateclaw.client.read-timeout` | int | `30000` | 读取超时时间（毫秒） |
| `mateclaw.client.default-workspace-id` | Long | `1L` | 默认工作区 ID |

**认证优先级：** `username/password` > `token`。若两者均未配置，启动时会抛出异常。

## 4. 认证机制

### 4.1 自动登录（推荐）

配置 `username` 和 `password` 后，SDK 会在首次请求时自动调用登录接口获取 JWT Token。Token 过期后自动重新登录，对调用方完全透明。

### 4.2 PAT 静态 Token

配置 `token` 为个人访问令牌（以 `mc_` 开头），适用于服务间调用场景，可设置为永不过期。

### 4.3 X-New-Token 续签

服务端在 Token 即将过期时，通过 `X-New-Token` 响应头返回新 Token。SDK 拦截器自动捕获并更新，实现滑动窗口续签。

### 4.4 401 自动重试

当收到 401 响应且使用 `LoginTokenProvider` 时，SDK 自动重新登录获取新 Token 并重试请求，最多重试一次。

## 5. 响应模型类体系

### 5.1 R\<T\> -- 通用响应封装

所有 API 调用均返回 `R<T>` 结构：

```java
public class R<T> {
    private int code;       // 状态码，200 表示成功
    private String msg;     // 提示信息
    private T data;         // 泛型数据

    public boolean isSuccess() {
        return code == 200;
    }
}
```

### 5.2 PageData\<T\> -- 分页数据封装

分页接口返回 `R<PageData<T>>`：

```java
public class PageData<T> {
    private List<T> content;        // 数据列表
    private long totalElements;     // 总记录数
    private int totalPages;         // 总页数
    private int number;             // 当前页码
    private int size;               // 每页大小
    private boolean first;          // 是否首页
    private boolean last;           // 是否末页
}
```

### 5.3 核心模型类一览

| 模块 | 模型类 | 说明 |
|------|--------|------|
| **Agent** | `Agent` | Agent 实体（name, agentType, enabled, systemPrompt 等） |
| | `AgentCapabilities` | Agent 能力信息（modelName, modalities 等） |
| | `AgentToolBinding` | Agent 工具绑定 |
| | `AgentSkillBinding` | Agent 技能绑定 |
| | `AgentProviderPreference` | Agent Provider 偏好 |
| | `Template` | Agent 模板 |
| **Workspace** | `Workspace` | 工作区实体（name, slug, description 等） |
| | `WorkspaceWithRole` | 工作区（含当前用户角色） |
| | `WorkspaceAccess` | 工作区访问权限 |
| | `WorkspaceMember` | 工作区成员 |
| | `WorkspaceFile` | 工作区文件 |
| **Conversation** | `Conversation` | 会话实体 |
| | `ConversationVO` | 会话视图（含 agentName, agentIcon, status 等） |
| | `Message` | 消息实体 |
| | `MessageContentPart` | 消息内容片段 |
| | `TokenUsageSummary` | Token 使用统计（byModel, byDate 嵌套） |
| | `ChatRequest` | 对话请求（conversationId, message, attachments） |
| **Channel** | `Channel` | 渠道实体 |
| | `ChannelSession` | 渠道会话 |
| **CronJob** | `CronJob` | 定时任务实体 |
| | `CronJobDTO` | 定时任务 DTO（含 agentName, channelName 等关联名称） |
| | `DeliveryConfig` | 投递配置 |
| | `CronJobRun` | 定时任务运行记录 |
| | `ActiveCronRun` | 活跃的定时任务运行 |
| **Dashboard** | `UsageDaily` | 每日使用量 |
| **Datasource** | `Datasource` | 数据源实体 |
| **Goal** | `Goal` | 目标实体 |
| | `GoalEvent` | 目标事件 |
| | `GoalEvaluationResult` | 目标评估结果 |
| | `GoalCreateRequest` | 目标创建请求 |
| | `GoalUpdateRequest` | 目标更新请求 |
| **LLM** | `ModelConfig` | 模型配置 |
| | `ModelProvider` | 模型供应商 |
| | `ModelInfo` | 模型信息 |
| | `ProviderInfo` | 供应商信息 |
| | `ModelSlotConfig` | 模型槽位配置 |
| | `ActiveModelsInfo` | 活动模型信息 |
| | `TestResult` | 连接测试结果 |
| | `DiscoverResult` | 模型发现结果 |
| | `EnableResult` | 启用结果 |
| **Tool** | `Tool` | 工具实体 |
| | `AvailableTool` | 可用工具 |
| | `McpServer` | MCP Server 实体 |
| | `McpToolDescriptor` | MCP 工具描述 |
| **Skill** | `Skill` | 技能实体 |
| | `ResolvedSkill` | 解析后的技能 |
| | `InstallResult` | 安装结果 |
| **Workflow** | `Workflow` | 工作流实体 |
| | `WorkflowRun` | 工作流运行 |
| | `WorkflowRunStep` | 工作流运行步骤 |
| | `WorkflowRunPause` | 工作流运行暂停 |
| **Wiki** | `WikiKnowledgeBase` | 知识库实体 |
| | `WikiPage` | 知识库页面 |
| | `WikiRawMaterial` | 原始材料 |
| | `WikiTransformation` | 转换规则 |
| | `WikiTransformationRun` | 转换运行记录 |
| | `WikiRelation` | 页面关联关系 |
| | `WikiHotCache` | 热缓存 |
| | `WikiChunk` | 知识库分块 |
| **Trigger** | `Trigger` | 触发器实体 |
| | `TriggerEvent` | 触发器事件 |
| **Memory** | `MemoryRecall` | 记忆回溯 |
| | `DreamReport` | 梦境报告 |
| | `Fact` | 事实 |
| **Auth** | `User` | 用户实体 |
| | `LoginRequest` | 登录请求（username, password） |
| | `LoginResponse` | 登录响应（token, username, nickname, role） |
| **Audit** | `AuditEvent` | 审计事件 |
| | `ToolApproval` | 工具审批 |
| **Security** | `SecurityFinding` | 安全发现 |
| **ACP** | `AcpEndpoint` | ACP 端点 |
| **System** | `SystemSettings` | 系统设置 |
| **Plugin** | `Plugin` | 插件实体 |
| | `PluginInfo` | 插件信息 |
| **Planning** | `Plan` | 计划 |
| | `SubPlan` | 子计划 |
| **Hook** | `Hook` | Hook 实体 |
| | `HookRun` | Hook 运行记录 |
| **Task** | `AsyncTaskInfo` | 异步任务信息 |

## 6. API 客户端列表

MateClawClient 提供以下 55 个 API 客户端，通过对应的访问方法获取：

| 访问方法 | 客户端类 | 功能说明 |
|----------|----------|----------|
| `auth()` | AuthClient | 认证管理（登录、用户管理） |
| `agent()` | AgentClient | Agent 管理（创建、配置、对话） |
| `channel()` | ChannelClient | 渠道管理（钉钉、飞书等） |
| `conversation()` | ConversationClient | 会话管理（创建、查询、删除） |
| `cronJob()` | CronJobClient | 定时任务管理 |
| `dashboard()` | DashboardClient | Dashboard 统计数据 |
| `datasource()` | DatasourceClient | 数据源管理 |
| `goal()` | GoalClient | 目标管理 |
| `mcpServer()` | McpServerClient | MCP Server 管理 |
| `memory()` | MemoryClient | 记忆管理 |
| `modelConfig()` | ModelConfigClient | 模型配置管理 |
| `providerPool()` | ProviderPoolClient | LLM Provider 可用池 |
| `skill()` | SkillClient | 技能管理 |
| `systemSetting()` | SystemSettingClient | 系统设置 |
| `tool()` | ToolClient | 工具管理 |
| `workspace()` | WorkspaceClient | 工作区管理 |
| `workspaceFile()` | WorkspaceFileClient | 工作区文件管理 |
| `tokenUsage()` | TokenUsageClient | Token 使用统计 |
| `setup()` | SetupClient | 系统初始化设置 |
| `chat()` | ChatClient | Chat 对话（SSE 流式） |
| `webChat()` | WebChatClient | WebChat 渠道对话 |
| `channelWebhook()` | ChannelWebhookClient | 渠道 Webhook 回调 |
| `channelQRCode()` | ChannelQRCodeClient | 渠道二维码注册 |
| `workflow()` | WorkflowClient | 工作流管理 |
| `workflowRun()` | WorkflowRunClient | 工作流运行管理 |
| `wiki()` | WikiClient | Wiki 知识库管理 |
| `wikiAdmin()` | WikiAdminClient | Wiki 管理功能 |
| `wikiTransformation()` | WikiTransformationClient | Wiki 转换管理 |
| `wikiRelation()` | WikiRelationClient | Wiki 关联查询 |
| `wikiHotCache()` | WikiHotCacheClient | Wiki 热缓存管理 |
| `wikiResearch()` | WikiResearchClient | Wiki 研究（SSE 流式） |
| `trigger()` | TriggerClient | 触发器管理 |
| `generatedFile()` | GeneratedFileClient | 生成文件下载 |
| `security()` | SecurityClient | 安全防护管理 |
| `featureFlag()` | FeatureFlagClient | 功能开关管理 |
| `skillTemplate()` | SkillTemplateClient | 技能模板管理 |
| `skillSecret()` | SkillSecretClient | 技能密钥管理 |
| `skillInstall()` | SkillInstallClient | 技能安装管理 |
| `agentRuntime()` | AgentRuntimeClient | Agent 运行时管理 |
| `agentBinding()` | AgentBindingClient | Agent 绑定管理 |
| `activityFeed()` | ActivityFeedClient | 活动流 |
| `tts()` | TtsClient | TTS 语音合成 |
| `stt()` | SttClient | STT 语音识别 |
| `auditEvent()` | AuditEventClient | 审计事件查询 |
| `personalAccessToken()` | PersonalAccessTokenClient | 个人访问令牌管理 |
| `template()` | TemplateClient | 模板管理 |
| `dream()` | DreamClient | Dream 梦境管理（SSE 流式） |
| `fact()` | FactClient | Fact 事实管理 |
| `oauth()` | OAuthClient | OAuth 认证管理 |
| `acpEndpoint()` | AcpEndpointClient | ACP 端点管理 |
| `plugin()` | PluginClient | 插件管理 |
| `planning()` | PlanningClient | 计划管理 |
| `notification()` | NotificationClient | 通知管理 |
| `systemHealth()` | SystemHealthClient | 系统健康检查 |
| `subagent()` | SubagentClient | 子 Agent 管理 |

## 7. 核心用法示例

### 7.1 获取 Agent 列表

```java
// 新方式：直接使用 Agent 模型类
R<List<Agent>> result = client.agent().list(null);
if (result.isSuccess()) {
    for (Agent agent : result.getData()) {
        System.out.println(agent.getName() + " - " + agent.getAgentType());
    }
}

// 旧方式（已废弃）：Map 访问，无编译期检查
// R<List<Map<String, Object>>> result = client.agent().list(null);
// String name = (String) result.getData().get(0).get("name"); // 容易拼写错误
```

### 7.2 获取工作区详情

```java
// 新方式：直接访问 Workspace 字段
R<Workspace> wsResult = client.workspace().get(1L);
if (wsResult.isSuccess()) {
    Workspace ws = wsResult.getData();
    String name = ws.getName();
    String slug = ws.getSlug();
}

// 旧方式（已废弃）：Map 取值需强转
// R<Map<String, Object>> wsResult = client.workspace().get(1L);
// String name = (String) wsResult.getData().get("name"); // 类型不安全
```

### 7.3 创建 Agent

```java
// 新方式：使用模型类构建请求体
Agent newAgent = new Agent();
newAgent.setName("MyAgent");
newAgent.setAgentType("chat");
newAgent.setEnabled(true);
R<Agent> created = client.agent().create(newAgent);

// 旧方式（已废弃）：Map 拼接
// Map<String, Object> newAgent = new HashMap<>();
// newAgent.put("name", "MyAgent"); // 字段名无校验
```

### 7.4 分页查询会话

```java
// 新方式：PageData<ConversationVO> 提供完整的分页信息
R<PageData<ConversationVO>> page = client.conversation().page(0, 20, null);
if (page.isSuccess()) {
    PageData<ConversationVO> data = page.getData();
    long total = data.getTotalElements();
    for (ConversationVO conv : data.getContent()) {
        System.out.println(conv.getTitle() + " by " + conv.getAgentName());
    }
}
```

### 7.5 Token 使用统计

```java
// 新方式：TokenUsageSummary 提供类型安全的嵌套结构
R<TokenUsageSummary> summary = client.tokenUsage().getSummary("2025-01-01", "2025-01-31", null, null);
if (summary.isSuccess()) {
    TokenUsageSummary data = summary.getData();
    System.out.println("Total prompt tokens: " + data.getTotalPromptTokens());
    for (TokenUsageSummary.ModelUsageItem item : data.getByModel()) {
        System.out.println(item.getRuntimeModel() + ": " + item.getPromptTokens());
    }
}
```

### 7.6 认证登录

```java
// 新方式：使用 LoginRequest / LoginResponse 模型类
LoginRequest request = new LoginRequest();
request.setUsername("admin");
request.setPassword("password");
R<LoginResponse> loginResult = client.auth().login(request);
if (loginResult.isSuccess()) {
    String token = loginResult.getData().getToken();
}
```

### 7.7 目标管理

```java
// 新方式：使用 GoalCreateRequest 创建目标
GoalCreateRequest req = new GoalCreateRequest();
req.setConversationId("conv-123");
req.setAgentId(1L);
req.setTitle("完成数据分析报告");
R<Goal> goal = client.goal().create(req);

// 评估目标
R<GoalEvaluationResult> eval = client.goal().evaluate(goal.getData().getId());
```

### 7.8 定时任务管理

```java
// 新方式：使用 CronJob 模型类创建，返回 CronJobDTO（含关联名称）
CronJob job = new CronJob();
job.setName("每日报告");
job.setCronExpression("0 0 9 * * ?");
job.setAgentId(1L);
job.setTriggerMessage("请生成今日报告");
R<CronJobDTO> created = client.cronJob().create(job);
if (created.isSuccess()) {
    System.out.println("Agent: " + created.getData().getAgentName());
}
```

### 7.9 Wiki 知识库

```java
// 新方式：使用 WikiKnowledgeBase 模型类
WikiKnowledgeBase kb = new WikiKnowledgeBase();
kb.setName("产品文档");
kb.setAgentId(1L);
R<WikiKnowledgeBase> created = client.wiki().createKB(kb);

// 获取知识库页面
R<List<WikiPage>> pages = client.wiki().listPages(created.getData().getId(), null);
```

## 8. SSE 流式接口使用

### 8.1 Chat 流式对话

```java
ChatRequest request = new ChatRequest();
request.setConversationId("conv-123");
request.setMessage("你好，请介绍一下自己");

client.chat().chatStream(request, new AbstractApiClient.SseStreamCallback() {
    @Override
    public void onData(String data) {
        System.out.println("收到数据: " + data);
    }

    @Override
    public void onComplete() {
        System.out.println("流式响应结束");
    }

    @Override
    public void onError(Exception e) {
        System.err.println("发生错误: " + e.getMessage());
    }
});
```

### 8.2 Wiki 处理进度

```java
client.wiki().subscribeProgress(kbId, new AbstractApiClient.SseStreamCallback() {
    @Override
    public void onData(String data) {
        System.out.println("处理进度: " + data);
    }

    @Override
    public void onComplete() {
        System.out.println("处理完成");
    }

    @Override
    public void onError(Exception e) {
        System.err.println("处理错误: " + e.getMessage());
    }
});
```

### 8.3 Wiki 研究流

```java
Map<String, Object> body = new HashMap<>();
body.put("query", "人工智能发展趋势");
body.put("depth", 3);
R<Map<String, Object>> result = client.wikiResearch().startResearch(body);
String sessionId = (String) result.getData().get("sessionId");

client.wikiResearch().stream(sessionId, new AbstractApiClient.SseStreamCallback() {
    @Override
    public void onData(String data) {
        System.out.println("研究进展: " + data);
    }

    @Override
    public void onComplete() {
        System.out.println("研究完成");
    }

    @Override
    public void onError(Exception e) {
        System.err.println("研究错误: " + e.getMessage());
    }
});
```

### 8.4 Dream 事件订阅

```java
client.dream().subscribeDreamEvents(agentId, new AbstractApiClient.SseStreamCallback() {
    @Override
    public void onData(String data) {
        System.out.println("梦境事件: " + data);
    }

    @Override
    public void onComplete() {
        System.out.println("梦境事件流结束");
    }

    @Override
    public void onError(Exception e) {
        System.err.println("梦境事件错误: " + e.getMessage());
    }
});
```

## 9. 高级用法

### 9.1 自定义 RestTemplate

```java
@Configuration
public class MyConfig {

    @Bean
    @Primary
    public RestTemplate mateclawRestTemplate(MateClawClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(60000);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(List.of(new MyCustomInterceptor()));
        return restTemplate;
    }
}
```

### 9.2 自定义 TokenProvider

```java
public class MyTokenProvider implements TokenProvider {

    private volatile String token;

    @Override
    public String getToken() {
        if (token == null || isExpired(token)) {
            refreshToken();
        }
        return token;
    }

    @Override
    public void refreshToken() {
        this.token = fetchTokenFromYourAuthSystem();
    }

    private boolean isExpired(String token) {
        // 检查 Token 是否过期
        return false;
    }

    private String fetchTokenFromYourAuthSystem() {
        // 从自定义认证系统获取 Token
        return "your-token";
    }
}

// 注册
@Bean
public TokenProvider mateclawTokenProvider() {
    return new MyTokenProvider();
}
```

### 9.3 动态切换工作区

```java
@Service
public class MultiWorkspaceService {

    private final MateClawClientProperties baseProperties;
    private final RestTemplate restTemplate;

    public MultiWorkspaceService(MateClawClientProperties properties, RestTemplate restTemplate) {
        this.baseProperties = properties;
        this.restTemplate = restTemplate;
    }

    public MateClawClient createClientForWorkspace(Long workspaceId) {
        MateClawClientProperties newProps = new MateClawClientProperties();
        newProps.setBaseUrl(baseProperties.getBaseUrl());
        newProps.setUsername(baseProperties.getUsername());
        newProps.setPassword(baseProperties.getPassword());
        newProps.setConnectTimeout(baseProperties.getConnectTimeout());
        newProps.setReadTimeout(baseProperties.getReadTimeout());
        newProps.setDefaultWorkspaceId(workspaceId);
        return new MateClawClient(newProps, restTemplate);
    }
}
```

### 9.4 错误处理

```java
public void safeApiCall() {
    try {
        R<List<Agent>> result = client.agent().list(null);
        if (result.isSuccess()) {
            List<Agent> agents = result.getData();
        } else {
            log.error("API 调用失败: code={}, msg={}", result.getCode(), result.getMsg());
        }
    } catch (HttpClientErrorException e) {
        log.error("客户端错误: {}", e.getResponseBodyAsString());
    } catch (HttpServerErrorException e) {
        log.error("服务端错误: {}", e.getResponseBodyAsString());
    } catch (ResourceAccessException e) {
        log.error("网络错误: {}", e.getMessage());
    }
}
```

## 10. 注意事项

1. **Token 管理**：SDK 自动管理 Token 的获取、续签和重新登录，无需手动处理
2. **工作区隔离**：大多数 API 会自动注入 `X-Workspace-Id` 请求头，确保数据隔离
3. **超时配置**：对于长时间运行的任务（如 Agent 对话），建议适当增加 `read-timeout`
4. **线程安全**：`MateClawClient` 是线程安全的，可以在多线程环境中共享使用
5. **模型类位置**：所有模型类位于 `vip.mate.client.model` 包下，无需依赖 server 模块
6. **SSE 回调**：流式接口通过回调处理，注意回调方法不要阻塞
7. **部分接口仍使用 Map**：少数接口（如 Agent.getState、Wiki.processKB）因返回结构复杂或动态，仍返回 `Map<String, Object>`

## 11. 常见问题 FAQ

**Q: 如何配置认证？**

A: 推荐使用 `username` 和 `password` 配置，SDK 会自动登录获取 JWT Token。若使用 PAT，可配置 `token`。两者同时配置时，`username/password` 优先。

**Q: Token 过期了怎么办？**

A: SDK 内置自动续签机制：服务端返回 `X-New-Token` 响应头时自动更新；收到 401 时自动重新登录并重试。

**Q: 如何切换工作区？**

A: 修改配置中的 `default-workspace-id`，或创建新的 `MateClawClient` 实例并指定不同的 `defaultWorkspaceId`。

**Q: 如何获取 PAT（个人访问令牌）？**

A: 登录后在系统设置中创建，或通过 `personalAccessToken()` 客户端创建。PAT 以 `mc_` 开头，可设置永不过期。

**Q: 连接超时怎么办？**

A: 检查 `base-url` 是否正确、网络是否可达，并适当增加 `connect-timeout` 和 `read-timeout`。
