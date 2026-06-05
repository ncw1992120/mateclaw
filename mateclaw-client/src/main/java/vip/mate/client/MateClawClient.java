package vip.mate.client;

import org.springframework.web.client.RestTemplate;
import vip.mate.client.api.*;
import vip.mate.client.properties.MateClawClientProperties;

/**
 * MateClaw 客户端主入口
 * <p>
 * 聚合所有 API 客户端，业务应用通过此类访问 MateClaw Server 的全部接口
 */
public class MateClawClient {

    private final AuthClient authClient;
    private final AgentClient agentClient;
    private final ChannelClient channelClient;
    private final ConversationClient conversationClient;
    private final CronJobClient cronJobClient;
    private final DashboardClient dashboardClient;
    private final DatasourceClient datasourceClient;
    private final GoalClient goalClient;
    private final McpServerClient mcpServerClient;
    private final MemoryClient memoryClient;
    private final ModelConfigClient modelConfigClient;
    private final ProviderPoolClient providerPoolClient;
    private final SkillClient skillClient;
    private final SystemSettingClient systemSettingClient;
    private final ToolClient toolClient;
    private final WorkspaceClient workspaceClient;
    private final WorkspaceFileClient workspaceFileClient;
    private final TokenUsageClient tokenUsageClient;
    private final SetupClient setupClient;
    private final ChatClient chatClient;
    private final WebChatClient webChatClient;
    private final ChannelWebhookClient channelWebhookClient;
    private final ChannelQRCodeClient channelQRCodeClient;
    private final WorkflowClient workflowClient;
    private final WorkflowRunClient workflowRunClient;
    private final WikiClient wikiClient;
    private final WikiAdminClient wikiAdminClient;
    private final WikiTransformationClient wikiTransformationClient;
    private final WikiRelationClient wikiRelationClient;
    private final WikiHotCacheClient wikiHotCacheClient;
    private final WikiResearchClient wikiResearchClient;
    private final TriggerClient triggerClient;
    private final GeneratedFileClient generatedFileClient;
    private final SecurityClient securityClient;
    private final FeatureFlagClient featureFlagClient;
    private final SkillTemplateClient skillTemplateClient;
    private final SkillSecretClient skillSecretClient;
    private final AgentRuntimeClient agentRuntimeClient;
    private final ActivityFeedClient activityFeedClient;
    private final TtsClient ttsClient;
    private final SttClient sttClient;
    private final AuditEventClient auditEventClient;
    private final PersonalAccessTokenClient personalAccessTokenClient;
    private final AgentBindingClient agentBindingClient;
    private final TemplateClient templateClient;
    private final SkillInstallClient skillInstallClient;
    private final DreamClient dreamClient;
    private final FactClient factClient;
    private final OAuthClient oAuthClient;
    private final AcpEndpointClient acpEndpointClient;
    private final PluginClient pluginClient;
    private final PlanningClient planningClient;
    private final NotificationClient notificationClient;
    private final SystemHealthClient systemHealthClient;
    private final SubagentClient subagentClient;

    public MateClawClient(MateClawClientProperties properties, RestTemplate restTemplate) {
        String baseUrl = properties.getBaseUrl();
        this.authClient = new AuthClient(baseUrl, restTemplate);
        this.agentClient = new AgentClient(baseUrl, restTemplate);
        this.channelClient = new ChannelClient(baseUrl, restTemplate);
        this.conversationClient = new ConversationClient(baseUrl, restTemplate);
        this.cronJobClient = new CronJobClient(baseUrl, restTemplate);
        this.dashboardClient = new DashboardClient(baseUrl, restTemplate);
        this.datasourceClient = new DatasourceClient(baseUrl, restTemplate);
        this.goalClient = new GoalClient(baseUrl, restTemplate);
        this.mcpServerClient = new McpServerClient(baseUrl, restTemplate);
        this.memoryClient = new MemoryClient(baseUrl, restTemplate);
        this.modelConfigClient = new ModelConfigClient(baseUrl, restTemplate);
        this.providerPoolClient = new ProviderPoolClient(baseUrl, restTemplate);
        this.skillClient = new SkillClient(baseUrl, restTemplate);
        this.systemSettingClient = new SystemSettingClient(baseUrl, restTemplate);
        this.toolClient = new ToolClient(baseUrl, restTemplate);
        this.workspaceClient = new WorkspaceClient(baseUrl, restTemplate);
        this.workspaceFileClient = new WorkspaceFileClient(baseUrl, restTemplate);
        this.tokenUsageClient = new TokenUsageClient(baseUrl, restTemplate);
        this.setupClient = new SetupClient(baseUrl, restTemplate);
        this.chatClient = new ChatClient(baseUrl, restTemplate);
        this.webChatClient = new WebChatClient(baseUrl, restTemplate);
        this.channelWebhookClient = new ChannelWebhookClient(baseUrl, restTemplate);
        this.channelQRCodeClient = new ChannelQRCodeClient(baseUrl, restTemplate);
        this.workflowClient = new WorkflowClient(baseUrl, restTemplate);
        this.workflowRunClient = new WorkflowRunClient(baseUrl, restTemplate);
        this.wikiClient = new WikiClient(baseUrl, restTemplate);
        this.wikiAdminClient = new WikiAdminClient(baseUrl, restTemplate);
        this.wikiTransformationClient = new WikiTransformationClient(baseUrl, restTemplate);
        this.wikiRelationClient = new WikiRelationClient(baseUrl, restTemplate);
        this.wikiHotCacheClient = new WikiHotCacheClient(baseUrl, restTemplate);
        this.wikiResearchClient = new WikiResearchClient(baseUrl, restTemplate);
        this.triggerClient = new TriggerClient(baseUrl, restTemplate);
        this.generatedFileClient = new GeneratedFileClient(baseUrl, restTemplate);
        this.securityClient = new SecurityClient(baseUrl, restTemplate);
        this.featureFlagClient = new FeatureFlagClient(baseUrl, restTemplate);
        this.skillTemplateClient = new SkillTemplateClient(baseUrl, restTemplate);
        this.skillSecretClient = new SkillSecretClient(baseUrl, restTemplate);
        this.agentRuntimeClient = new AgentRuntimeClient(baseUrl, restTemplate);
        this.activityFeedClient = new ActivityFeedClient(baseUrl, restTemplate);
        this.ttsClient = new TtsClient(baseUrl, restTemplate);
        this.sttClient = new SttClient(baseUrl, restTemplate);
        this.auditEventClient = new AuditEventClient(baseUrl, restTemplate);
        this.personalAccessTokenClient = new PersonalAccessTokenClient(baseUrl, restTemplate);
        this.agentBindingClient = new AgentBindingClient(baseUrl, restTemplate);
        this.templateClient = new TemplateClient(baseUrl, restTemplate);
        this.skillInstallClient = new SkillInstallClient(baseUrl, restTemplate);
        this.dreamClient = new DreamClient(baseUrl, restTemplate);
        this.factClient = new FactClient(baseUrl, restTemplate);
        this.oAuthClient = new OAuthClient(baseUrl, restTemplate);
        this.acpEndpointClient = new AcpEndpointClient(baseUrl, restTemplate);
        this.pluginClient = new PluginClient(baseUrl, restTemplate);
        this.planningClient = new PlanningClient(baseUrl, restTemplate);
        this.notificationClient = new NotificationClient(baseUrl, restTemplate);
        this.systemHealthClient = new SystemHealthClient(baseUrl, restTemplate);
        this.subagentClient = new SubagentClient(baseUrl, restTemplate);
    }

    public AuthClient auth() { return authClient; }
    public AgentClient agent() { return agentClient; }
    public ChannelClient channel() { return channelClient; }
    public ConversationClient conversation() { return conversationClient; }
    public CronJobClient cronJob() { return cronJobClient; }
    public DashboardClient dashboard() { return dashboardClient; }
    public DatasourceClient datasource() { return datasourceClient; }
    public GoalClient goal() { return goalClient; }
    public McpServerClient mcpServer() { return mcpServerClient; }
    public MemoryClient memory() { return memoryClient; }
    public ModelConfigClient modelConfig() { return modelConfigClient; }
    public ProviderPoolClient providerPool() { return providerPoolClient; }
    public SkillClient skill() { return skillClient; }
    public SystemSettingClient systemSetting() { return systemSettingClient; }
    public ToolClient tool() { return toolClient; }
    public WorkspaceClient workspace() { return workspaceClient; }
    public WorkspaceFileClient workspaceFile() { return workspaceFileClient; }
    public TokenUsageClient tokenUsage() { return tokenUsageClient; }
    public SetupClient setup() { return setupClient; }
    public ChatClient chat() { return chatClient; }
    public WebChatClient webChat() { return webChatClient; }
    public ChannelWebhookClient channelWebhook() { return channelWebhookClient; }
    public ChannelQRCodeClient channelQRCode() { return channelQRCodeClient; }
    public WorkflowClient workflow() { return workflowClient; }
    public WorkflowRunClient workflowRun() { return workflowRunClient; }
    public WikiClient wiki() { return wikiClient; }
    public WikiAdminClient wikiAdmin() { return wikiAdminClient; }
    public WikiTransformationClient wikiTransformation() { return wikiTransformationClient; }
    public WikiRelationClient wikiRelation() { return wikiRelationClient; }
    public WikiHotCacheClient wikiHotCache() { return wikiHotCacheClient; }
    public WikiResearchClient wikiResearch() { return wikiResearchClient; }
    public TriggerClient trigger() { return triggerClient; }
    public GeneratedFileClient generatedFile() { return generatedFileClient; }
    public SecurityClient security() { return securityClient; }
    public FeatureFlagClient featureFlag() { return featureFlagClient; }
    public SkillTemplateClient skillTemplate() { return skillTemplateClient; }
    public SkillSecretClient skillSecret() { return skillSecretClient; }
    public AgentRuntimeClient agentRuntime() { return agentRuntimeClient; }
    public ActivityFeedClient activityFeed() { return activityFeedClient; }
    public TtsClient tts() { return ttsClient; }
    public SttClient stt() { return sttClient; }
    public AuditEventClient auditEvent() { return auditEventClient; }
    public PersonalAccessTokenClient personalAccessToken() { return personalAccessTokenClient; }
    public AgentBindingClient agentBinding() { return agentBindingClient; }
    public TemplateClient template() { return templateClient; }
    public SkillInstallClient skillInstall() { return skillInstallClient; }
    public DreamClient dream() { return dreamClient; }
    public FactClient fact() { return factClient; }
    public OAuthClient oauth() { return oAuthClient; }
    public AcpEndpointClient acpEndpoint() { return acpEndpointClient; }
    public PluginClient plugin() { return pluginClient; }
    public PlanningClient planning() { return planningClient; }
    public NotificationClient notification() { return notificationClient; }
    public SystemHealthClient systemHealth() { return systemHealthClient; }
    public SubagentClient subagent() { return subagentClient; }
}
