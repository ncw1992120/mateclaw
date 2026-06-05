package vip.mate.client.constant;

/**
 * MateClaw API 路径常量
 * <p>
 * 统一管理所有 HTTP 接口路径，便于维护和修改
 */
public final class ApiPathConstants {

    private ApiPathConstants() {
        // 私有构造函数，防止实例化
    }

    // ==================== API 基础路径 ====================

    /** API 版本前缀 */
    public static final String API_V1 = "/api/v1";

    // ==================== 认证模块 ====================

    /** 认证基础路径 */
    public static final String AUTH = API_V1 + "/auth";

    /** 登录 */
    public static final String AUTH_LOGIN = AUTH + "/login";

    /** 用户列表 */
    public static final String AUTH_USERS = AUTH + "/users";

    /** 修改密码 */
    public static final String AUTH_USER_PASSWORD = AUTH + "/users/{id}/password";

    // ==================== Agent 模块 ====================

    /** Agent 基础路径 */
    public static final String AGENT = API_V1 + "/agents";

    /** Agent 详情/更新/删除 */
    public static final String AGENT_BY_ID = AGENT + "/{id}";

    /** Agent 能力信息 */
    public static final String AGENT_CAPABILITIES = AGENT + "/{id}/capabilities";

    /** Agent 对话 */
    public static final String AGENT_CHAT = AGENT + "/{id}/chat";

    /** Agent 执行任务 */
    public static final String AGENT_EXECUTE = AGENT + "/{id}/execute";

    /** Agent 状态 */
    public static final String AGENT_STATE = AGENT + "/{id}/state";

    /** Agent 工作区文件 */
    public static final String AGENT_WORKSPACE_FILES = AGENT + "/{agentId}/workspace/files";

    /** Agent 工作区文件详情 */
    public static final String AGENT_WORKSPACE_FILE_BY_NAME = AGENT + "/{agentId}/workspace/files/{filename}";

    /** Agent 提示词文件列表 */
    public static final String AGENT_PROMPT_FILES = AGENT + "/{agentId}/workspace/prompt-files";

    // ==================== 渠道模块 ====================

    /** 渠道基础路径 */
    public static final String CHANNEL = API_V1 + "/channels";

    /** 按类型获取渠道 */
    public static final String CHANNEL_BY_TYPE = CHANNEL + "/type/{channelType}";

    /** 渠道详情/更新/删除 */
    public static final String CHANNEL_BY_ID = CHANNEL + "/{id}";

    /** 渠道启用/禁用 */
    public static final String CHANNEL_TOGGLE = CHANNEL + "/{id}/toggle";

    /** 渠道状态 */
    public static final String CHANNEL_STATUS = CHANNEL + "/status";

    /** 渠道健康检查 */
    public static final String CHANNEL_HEALTH = CHANNEL + "/health";

    /** 单个渠道健康检查 */
    public static final String CHANNEL_HEALTH_BY_ID = CHANNEL + "/{id}/health";

    /** 渠道预检 */
    public static final String CHANNEL_PREFLIGHT = CHANNEL + "/preflight";

    // ==================== 会话模块 ====================

    /** 会话基础路径 */
    public static final String CONVERSATION = API_V1 + "/conversations";

    /** 会话分页查询 */
    public static final String CONVERSATION_PAGE = CONVERSATION + "/page";

    /** 会话详情/删除 */
    public static final String CONVERSATION_BY_ID = CONVERSATION + "/{conversationId}";

    /** 会话消息列表 */
    public static final String CONVERSATION_MESSAGES = CONVERSATION + "/{conversationId}/messages";

    /** 会话重命名 */
    public static final String CONVERSATION_TITLE = CONVERSATION + "/{conversationId}/title";

    /** 会话置顶 */
    public static final String CONVERSATION_PIN = CONVERSATION + "/{conversationId}/pin";

    /** 会话模型设置 */
    public static final String CONVERSATION_MODEL = CONVERSATION + "/{conversationId}/model";

    /** 批量删除会话 */
    public static final String CONVERSATION_BATCH_DELETE = CONVERSATION + "/batch-delete";

    /** 会话流状态 */
    public static final String CONVERSATION_STATUS = CONVERSATION + "/{conversationId}/status";

    // ==================== 定时任务模块 ====================

    /** 定时任务基础路径 */
    public static final String CRON_JOB = API_V1 + "/cron-jobs";

    /** 定时任务详情/更新/删除 */
    public static final String CRON_JOB_BY_ID = CRON_JOB + "/{id}";

    /** 定时任务启用/禁用 */
    public static final String CRON_JOB_TOGGLE = CRON_JOB + "/{id}/toggle";

    /** 立即执行定时任务 */
    public static final String CRON_JOB_RUN = CRON_JOB + "/{id}/run";

    /** 活动中的任务运行 */
    public static final String CRON_JOB_ACTIVE_RUNS = CRON_JOB + "/active-runs";

    // ==================== Dashboard 模块 ====================

    /** Dashboard 基础路径 */
    public static final String DASHBOARD = API_V1 + "/dashboard";

    /** Dashboard 概览 */
    public static final String DASHBOARD_OVERVIEW = DASHBOARD + "/overview";

    /** Dashboard 趋势 */
    public static final String DASHBOARD_TREND = DASHBOARD + "/trend";

    /** 定时任务运行记录 */
    public static final String DASHBOARD_CRON_RUNS = DASHBOARD + "/cron-runs";

    /** 单个定时任务运行记录 */
    public static final String DASHBOARD_CRON_RUNS_BY_ID = DASHBOARD + "/cron-runs/{cronJobId}";

    // ==================== 数据源模块 ====================

    /** 数据源基础路径 */
    public static final String DATASOURCE = API_V1 + "/datasources";

    /** 数据源详情/更新/删除 */
    public static final String DATASOURCE_BY_ID = DATASOURCE + "/{id}";

    /** 数据源连接测试 */
    public static final String DATASOURCE_TEST = DATASOURCE + "/{id}/test";

    /** 数据源启用/禁用 */
    public static final String DATASOURCE_TOGGLE = DATASOURCE + "/{id}/toggle";

    // ==================== 目标模块 ====================

    /** 目标基础路径 */
    public static final String GOAL = API_V1 + "/goals";

    /** 目标详情/更新 */
    public static final String GOAL_BY_ID = GOAL + "/{id}";

    /** 按会话获取活动目标 */
    public static final String GOAL_BY_CONVERSATION = GOAL + "/by-conversation/{conversationId}";

    /** 目标事件 */
    public static final String GOAL_EVENTS = GOAL + "/{id}/events";

    /** 暂停目标 */
    public static final String GOAL_PAUSE = GOAL + "/{id}/pause";

    /** 恢复目标 */
    public static final String GOAL_RESUME = GOAL + "/{id}/resume";

    /** 放弃目标 */
    public static final String GOAL_ABANDON = GOAL + "/{id}/abandon";

    /** 添加退出条件 */
    public static final String GOAL_CRITERIA = GOAL + "/{id}/criteria";

    // ==================== MCP Server 模块 ====================

    /** MCP Server 基础路径 */
    public static final String MCP_SERVER = API_V1 + "/mcp/servers";

    /** MCP Server 详情/更新/删除 */
    public static final String MCP_SERVER_BY_ID = MCP_SERVER + "/{id}";

    /** MCP Server 启用/禁用 */
    public static final String MCP_SERVER_TOGGLE = MCP_SERVER + "/{id}/toggle";

    /** MCP Server 披露层级 */
    public static final String MCP_SERVER_DISCLOSURE_TIER = MCP_SERVER + "/{id}/disclosure-tier";

    /** MCP Server 测试 */
    public static final String MCP_SERVER_TEST = MCP_SERVER + "/{id}/test";

    /** MCP Server 工具列表 */
    public static final String MCP_SERVER_TOOLS = MCP_SERVER + "/{id}/tools";

    /** 刷新所有 MCP Server */
    public static final String MCP_SERVER_REFRESH = MCP_SERVER + "/refresh";

    // ==================== 记忆模块 ====================

    /** 记忆基础路径 */
    public static final String MEMORY = API_V1 + "/memory";

    /** 触发涌现记忆 */
    public static final String MEMORY_EMERGENCE = MEMORY + "/{agentId}/emergence";

    /** 触发聚焦梦境 */
    public static final String MEMORY_FOCUSED_DREAM = MEMORY + "/{agentId}/dreaming/focused";

    /** 触发会话总结 */
    public static final String MEMORY_SUMMARIZE = MEMORY + "/{agentId}/summarize/{conversationId}";

    /** 梦境状态 */
    public static final String MEMORY_DREAMING_STATUS = MEMORY + "/{agentId}/dreaming/status";

    /** 梦境候选 */
    public static final String MEMORY_DREAMING_CANDIDATES = MEMORY + "/{agentId}/dreaming/candidates";

    /** 梦境记录 */
    public static final String MEMORY_DREAMS = MEMORY + "/{agentId}/dreaming/dreams";

    // ==================== 模型配置模块 ====================

    /** 模型配置基础路径 */
    public static final String MODEL = API_V1 + "/models";

    /** 模型目录 */
    public static final String MODEL_CATALOG = MODEL + "/catalog";

    /** 已启用的 Provider */
    public static final String MODEL_ENABLED = MODEL + "/enabled";

    /** 默认模型 */
    public static final String MODEL_DEFAULT = MODEL + "/default";

    /** 活动模型 */
    public static final String MODEL_ACTIVE = MODEL + "/active";

    /** 启用 Provider */
    public static final String MODEL_PROVIDER_ENABLE = MODEL + "/{providerId}/enable";

    /** 禁用 Provider */
    public static final String MODEL_PROVIDER_DISABLE = MODEL + "/{providerId}/disable";

    /** Provider 配置 */
    public static final String MODEL_PROVIDER_CONFIG = MODEL + "/{providerId}/config";

    /** Provider 连接测试 */
    public static final String MODEL_PROVIDER_TEST = MODEL + "/{providerId}/test-connection";

    /** Provider 模型发现 */
    public static final String MODEL_PROVIDER_DISCOVER = MODEL + "/{providerId}/discover";

    /** 应用发现的模型 */
    public static final String MODEL_PROVIDER_DISCOVER_APPLY = MODEL + "/{providerId}/discover/apply";

    /** Provider 模型测试 */
    public static final String MODEL_PROVIDER_MODEL_TEST = MODEL + "/{providerId}/models/test";

    /** Provider 模型列表 */
    public static final String MODEL_PROVIDER_MODELS = MODEL + "/{providerId}/models";

    /** 自定义 Provider */
    public static final String MODEL_CUSTOM_PROVIDER = MODEL + "/custom-providers";

    /** 自定义 Provider 详情/删除 */
    public static final String MODEL_CUSTOM_PROVIDER_BY_ID = MODEL + "/custom-providers/{providerId}";

    /** 模型详情/更新/删除 */
    public static final String MODEL_BY_ID = MODEL + "/{id}";

    /** 设置默认模型 */
    public static final String MODEL_SET_DEFAULT = MODEL + "/{id}/default";

    /** 按类型获取模型 */
    public static final String MODEL_BY_TYPE = MODEL + "/by-type";

    /** Embedding 模型测试 */
    public static final String MODEL_EMBEDDING_TEST = MODEL + "/embedding/{modelId}/test";

    /** 默认 Embedding 模型 */
    public static final String MODEL_EMBEDDING_DEFAULT = MODEL + "/embedding/default";

    // ==================== Provider 池模块 ====================

    /** Provider 池基础路径 */
    public static final String PROVIDER_POOL = API_V1 + "/llm/provider-pool";

    /** Provider 重新探测 */
    public static final String PROVIDER_POOL_REPROBE = PROVIDER_POOL + "/{providerId}/reprobe";

    // ==================== 技能模块 ====================

    /** 技能基础路径 */
    public static final String SKILL = API_V1 + "/skills";

    /** 技能统计 */
    public static final String SKILL_COUNTS = SKILL + "/counts";

    /** 已启用的技能 */
    public static final String SKILL_ENABLED = SKILL + "/enabled";

    /** 按类型获取技能 */
    public static final String SKILL_BY_TYPE = SKILL + "/type/{skillType}";

    /** 技能摘要 */
    public static final String SKILL_SUMMARY = SKILL + "/summary";

    /** 技能详情/更新/删除 */
    public static final String SKILL_BY_ID = SKILL + "/{id}";

    /** 技能启用/禁用 */
    public static final String SKILL_TOGGLE = SKILL + "/{id}/toggle";

    /** 技能重新扫描 */
    public static final String SKILL_RESCAN = SKILL + "/{id}/rescan";

    /** 技能文件同步 */
    public static final String SKILL_SYNC_FILES = SKILL + "/{id}/sync-files";

    /** 所有技能文件同步 */
    public static final String SKILL_SYNC_ALL_FILES = SKILL + "/sync-files";

    /** 提示词预览 */
    public static final String SKILL_PROMPT_PREVIEW = SKILL + "/prompt-preview";

    /** 运行时活动技能 */
    public static final String SKILL_RUNTIME_ACTIVE = SKILL + "/runtime/active";

    /** 运行时状态 */
    public static final String SKILL_RUNTIME_STATUS = SKILL + "/runtime/status";

    /** 刷新运行时 */
    public static final String SKILL_RUNTIME_REFRESH = SKILL + "/runtime/refresh";

    /** 技能依赖 */
    public static final String SKILL_REQUIREMENTS = SKILL + "/{id}/requirements";

    /** 技能员工 */
    public static final String SKILL_EMPLOYEES = SKILL + "/{id}/employees";

    /** 技能课程 */
    public static final String SKILL_LESSONS = SKILL + "/{id}/lessons";

    /** 清空技能课程 */
    public static final String SKILL_LESSONS_CLEAR = SKILL + "/{id}/lessons/clear";

    /** 从会话合成技能 */
    public static final String SKILL_SYNTHESIZE = SKILL + "/synthesize-from-conversation";

    /** 导出技能到工作区 */
    public static final String SKILL_EXPORT_WORKSPACE = SKILL + "/{id}/export-workspace";

    /** 技能工作区信息 */
    public static final String SKILL_WORKSPACE_INFO = SKILL + "/{id}/workspace";

    /** 技能置顶 */
    public static final String SKILL_PIN = SKILL + "/{id}/pin";

    /** 技能归档 */
    public static final String SKILL_ARCHIVE = SKILL + "/{id}/archive";

    /** 技能恢复 */
    public static final String SKILL_RESTORE = SKILL + "/{id}/restore";

    /** Curator 试运行 */
    public static final String SKILL_CURATOR_DRY_RUN = SKILL + "/curator/dry-run";

    /** Curator 激活 */
    public static final String SKILL_CURATOR_ACTIVATE = SKILL + "/curator/activate";

    /** Curator 暂停 */
    public static final String SKILL_CURATOR_PAUSE = SKILL + "/curator/pause";

    /** Curator 恢复 */
    public static final String SKILL_CURATOR_RESUME = SKILL + "/curator/resume";

    /** Curator 状态 */
    public static final String SKILL_CURATOR_STATUS = SKILL + "/curator/status";

    /** Curator 报告列表 */
    public static final String SKILL_CURATOR_REPORTS = SKILL + "/curator/reports";

    /** Curator 报告详情 */
    public static final String SKILL_CURATOR_REPORT_BY_ID = SKILL + "/curator/reports/{runId}";

    // ==================== 系统设置模块 ====================

    /** 系统设置基础路径 */
    public static final String SETTINGS = API_V1 + "/settings";

    /** 系统语言 */
    public static final String SETTINGS_LANGUAGE = SETTINGS + "/language";

    /** Sidecar 配置 */
    public static final String SETTINGS_SIDECAR = SETTINGS + "/sidecar";

    // ==================== 工具模块 ====================

    /** 工具基础路径 */
    public static final String TOOL = API_V1 + "/tools";

    /** 已启用的工具 */
    public static final String TOOL_ENABLED = TOOL + "/enabled";

    /** 可用工具 */
    public static final String TOOL_AVAILABLE = TOOL + "/available";

    /** 工具详情/更新/删除 */
    public static final String TOOL_BY_ID = TOOL + "/{id}";

    /** 工具启用/禁用 */
    public static final String TOOL_TOGGLE = TOOL + "/{id}/toggle";

    /** 工具披露层级 */
    public static final String TOOL_DISCLOSURE_TIER = TOOL + "/{id}/disclosure-tier";

    // ==================== 工作区模块 ====================

    /** 工作区基础路径 */
    public static final String WORKSPACE = API_V1 + "/workspaces";

    /** 工作区详情/更新/删除 */
    public static final String WORKSPACE_BY_ID = WORKSPACE + "/{id}";

    /** 工作区访问权限 */
    public static final String WORKSPACE_ACCESS = WORKSPACE + "/{id}/access";

    /** 工作区成员列表 */
    public static final String WORKSPACE_MEMBERS = WORKSPACE + "/{id}/members";

    /** 工作区成员详情/更新/删除 */
    public static final String WORKSPACE_MEMBER_BY_ID = WORKSPACE + "/{id}/members/{targetUserId}";

    // ==================== Token 使用统计模块 ====================

    /** Token 使用统计基础路径 */
    public static final String TOKEN_USAGE = API_V1 + "/token-usage";

    // ==================== 初始化设置模块 ====================

    /** 初始化设置基础路径 */
    public static final String SETUP = API_V1 + "/setup";

    /** 初始化状态 */
    public static final String SETUP_STATUS = SETUP + "/status";

    /** 执行初始化 */
    public static final String SETUP_INIT = SETUP + "/init";

    /** 引导状态 */
    public static final String SETUP_ONBOARDING_STATUS = SETUP + "/onboarding-status";

    // ==================== Chat 对话模块 ====================

    /** Chat 基础路径 */
    public static final String CHAT = API_V1 + "/chat";

    /** Chat SSE 流式对话 */
    public static final String CHAT_STREAM = CHAT + "/stream";

    /** Chat 停止流式对话 */
    public static final String CHAT_STOP = CHAT + "/{conversationId}/stop";

    /** Chat 中断流式对话 */
    public static final String CHAT_INTERRUPT = CHAT + "/{conversationId}/interrupt";

    /** Chat 文件上传 */
    public static final String CHAT_UPLOAD = CHAT + "/upload";

    /** Chat 文件下载 */
    public static final String CHAT_FILE = CHAT + "/files/{conversationId}/{storedName}";

    /** Chat 待审批列表 */
    public static final String CHAT_PENDING_APPROVALS = CHAT + "/{conversationId}/pending-approvals";

    // ==================== WebChat 渠道对话模块 ====================

    /** WebChat 基础路径 */
    public static final String WEBCHAT = API_V1 + "/channels/webchat";

    /** WebChat SSE 流式对话 */
    public static final String WEBCHAT_STREAM = WEBCHAT + "/stream";

    /** WebChat 配置 */
    public static final String WEBCHAT_CONFIG = WEBCHAT + "/config";

    // ==================== 渠道 Webhook 模块 ====================

    /** 渠道 Webhook 基础路径 */
    public static final String CHANNEL_WEBHOOK = API_V1 + "/channels/webhook";

    /** 钉钉 Webhook */
    public static final String CHANNEL_WEBHOOK_DINGTALK = CHANNEL_WEBHOOK + "/dingtalk";

    /** 钉钉注册开始 */
    public static final String CHANNEL_WEBHOOK_DINGTALK_REGISTER_BEGIN = CHANNEL_WEBHOOK + "/dingtalk/register/begin";

    /** 钉钉注册状态 */
    public static final String CHANNEL_WEBHOOK_DINGTALK_REGISTER_STATUS = CHANNEL_WEBHOOK + "/dingtalk/register/status";

    /** 飞书 Webhook */
    public static final String CHANNEL_WEBHOOK_FEISHU = CHANNEL_WEBHOOK + "/feishu";

    /** 飞书注册开始 */
    public static final String CHANNEL_WEBHOOK_FEISHU_REGISTER_BEGIN = CHANNEL_WEBHOOK + "/feishu/register/begin";

    /** 飞书注册状态 */
    public static final String CHANNEL_WEBHOOK_FEISHU_REGISTER_STATUS = CHANNEL_WEBHOOK + "/feishu/register/status";

    /** Telegram Webhook */
    public static final String CHANNEL_WEBHOOK_TELEGRAM = CHANNEL_WEBHOOK + "/telegram";

    /** Discord Webhook */
    public static final String CHANNEL_WEBHOOK_DISCORD = CHANNEL_WEBHOOK + "/discord";

    /** 企业微信 Webhook */
    public static final String CHANNEL_WEBHOOK_WECOM = CHANNEL_WEBHOOK + "/wecom";

    /** Slack Webhook */
    public static final String CHANNEL_WEBHOOK_SLACK = CHANNEL_WEBHOOK + "/slack";

    /** 微信二维码 */
    public static final String CHANNEL_WEBHOOK_WEIXIN_QRCODE = CHANNEL_WEBHOOK + "/weixin/qrcode";

    /** 微信二维码状态 */
    public static final String CHANNEL_WEBHOOK_WEIXIN_QRCODE_STATUS = CHANNEL_WEBHOOK + "/weixin/qrcode/status";

    /** Webhook 状态 */
    public static final String CHANNEL_WEBHOOK_STATUS = CHANNEL_WEBHOOK + "/status";

    // ==================== 渠道二维码模块 ====================

    /** 渠道二维码基础路径 */
    public static final String CHANNEL_QRCODE = API_V1 + "/channels/qrcode";

    /** 渠道二维码注册开始 */
    public static final String CHANNEL_QRCODE_BEGIN = CHANNEL_QRCODE + "/{channelType}/begin";

    /** 渠道二维码注册状态 */
    public static final String CHANNEL_QRCODE_STATUS = CHANNEL_QRCODE + "/{channelType}/status";

    // ==================== 工作流模块 ====================

    /** 工作流基础路径 */
    public static final String WORKFLOW = API_V1 + "/workflows";

    /** 工作流详情/更新/删除 */
    public static final String WORKFLOW_BY_ID = WORKFLOW + "/{id}";

    /** 工作流保存草稿 */
    public static final String WORKFLOW_DRAFT = WORKFLOW + "/{id}/draft";

    /** 工作流编译 */
    public static final String WORKFLOW_COMPILE = WORKFLOW + "/{id}/compile";

    /** 工作流发布 */
    public static final String WORKFLOW_PUBLISH = WORKFLOW + "/{id}/publish";

    /** 工作流运行记录 */
    public static final String WORKFLOW_RUNS = WORKFLOW + "/{id}/runs";

    /** 工作流暂停的运行 */
    public static final String WORKFLOW_RUNS_PAUSED = WORKFLOW + "/runs/paused";

    /** 工作流运行详情 */
    public static final String WORKFLOW_RUN_BY_ID = WORKFLOW + "/runs/{runId}";

    /** 工作流草稿生成 */
    public static final String WORKFLOW_DRAFT_GENERATE = WORKFLOW + "/draft/generate";

    /** 工作流草稿模板列表 */
    public static final String WORKFLOW_DRAFT_TEMPLATES = WORKFLOW + "/draft/templates";

    /** 工作流草稿预览编译 */
    public static final String WORKFLOW_DRAFT_PREVIEW_COMPILE = WORKFLOW + "/draft/preview-compile";

    // ==================== 工作流运行模块 ====================

    /** 工作流运行基础路径 */
    public static final String WORKFLOW_RUN = API_V1 + "/workflows/runs";

    /** 工作流运行恢复 */
    public static final String WORKFLOW_RUN_RESUME = WORKFLOW_RUN + "/{runId}/resume";

    // ==================== Wiki 知识库模块 ====================

    /** Wiki 基础路径 */
    public static final String WIKI = API_V1 + "/wiki";

    /** 知识库列表 */
    public static final String WIKI_KB = WIKI + "/knowledge-bases";

    /** 知识库详情/更新/删除 */
    public static final String WIKI_KB_BY_ID = WIKI + "/knowledge-bases/{id}";

    /** 按 Agent 获取知识库 */
    public static final String WIKI_KB_BY_AGENT = WIKI + "/knowledge-bases/agent/{agentId}";

    /** 知识库配置 */
    public static final String WIKI_KB_CONFIG = WIKI + "/knowledge-bases/{id}/config";

    /** 知识库关联目录 */
    public static final String WIKI_KB_SOURCE_DIRECTORY = WIKI + "/knowledge-bases/{id}/source-directory";

    /** 知识库扫描目录 */
    public static final String WIKI_KB_SCAN = WIKI + "/knowledge-bases/{id}/scan";

    /** 知识库原始材料列表 */
    public static final String WIKI_KB_RAW = WIKI + "/knowledge-bases/{kbId}/raw";

    /** 知识库添加文本材料 */
    public static final String WIKI_KB_RAW_TEXT = WIKI + "/knowledge-bases/{kbId}/raw/text";

    /** 知识库上传文件材料 */
    public static final String WIKI_KB_RAW_UPLOAD = WIKI + "/knowledge-bases/{kbId}/raw/upload";

    /** 知识库删除原始材料 */
    public static final String WIKI_KB_RAW_BY_ID = WIKI + "/knowledge-bases/{kbId}/raw/{rawId}";

    /** 知识库重新处理原始材料 */
    public static final String WIKI_KB_RAW_REPROCESS = WIKI + "/knowledge-bases/{kbId}/raw/{rawId}/reprocess";

    /** 知识库取消原始材料处理 */
    public static final String WIKI_KB_RAW_CANCEL = WIKI + "/knowledge-bases/{kbId}/raw/{rawId}/cancel";

    /** 知识库下载原始材料 */
    public static final String WIKI_KB_RAW_DOWNLOAD = WIKI + "/knowledge-bases/{kbId}/raw/{rawId}/download";

    /** 知识库页面列表 */
    public static final String WIKI_KB_PAGES = WIKI + "/knowledge-bases/{kbId}/pages";

    /** 知识库页面详情/更新/删除 */
    public static final String WIKI_KB_PAGE_BY_SLUG = WIKI + "/knowledge-bases/{kbId}/pages/{slug}";

    /** 知识库批量删除页面 */
    public static final String WIKI_KB_PAGES_BATCH = WIKI + "/knowledge-bases/{kbId}/pages/batch";

    /** 知识库页面反向链接 */
    public static final String WIKI_KB_PAGE_BACKLINKS = WIKI + "/knowledge-bases/{kbId}/pages/{slug}/backlinks";

    /** 知识库归档页面列表 */
    public static final String WIKI_KB_PAGES_ARCHIVED = WIKI + "/knowledge-bases/{kbId}/pages/archived";

    /** 知识库归档页面 */
    public static final String WIKI_KB_PAGE_ARCHIVE = WIKI + "/knowledge-bases/{kbId}/pages/{slug}/archive";

    /** 知识库取消归档页面 */
    public static final String WIKI_KB_PAGE_UNARCHIVE = WIKI + "/knowledge-bases/{kbId}/pages/{slug}/unarchive";

    /** 知识库触发处理 */
    public static final String WIKI_KB_PROCESS = WIKI + "/knowledge-bases/{kbId}/process";

    /** 知识库处理状态 */
    public static final String WIKI_KB_PROCESSING_STATUS = WIKI + "/knowledge-bases/{kbId}/processing-status";

    /** 知识库处理进度 SSE */
    public static final String WIKI_KB_PROGRESS = WIKI + "/knowledge-bases/{kbId}/progress";

    // ==================== Wiki 管理模块 ====================

    /** Wiki 管理基础路径 */
    public static final String WIKI_ADMIN = WIKI + "/admin";

    /** 重建知识库概览 */
    public static final String WIKI_ADMIN_KB_REBUILD_OVERVIEW = WIKI_ADMIN + "/kb/{kbId}/rebuild-overview";

    /** 回填 Token 数 */
    public static final String WIKI_ADMIN_BACKFILL_TOKENS = WIKI_ADMIN + "/backfill-tokens";

    // ==================== Wiki 转换模块 ====================

    /** Wiki 转换基础路径 */
    public static final String WIKI_TRANSFORMATION = WIKI + "/transformations";

    /** Wiki 转换详情/更新/删除 */
    public static final String WIKI_TRANSFORMATION_BY_ID = WIKI_TRANSFORMATION + "/{id}";

    /** Wiki 转换应用 */
    public static final String WIKI_TRANSFORMATION_APPLY = WIKI_TRANSFORMATION + "/{id}/apply";

    /** Wiki 转换聚合 */
    public static final String WIKI_TRANSFORMATION_AGGREGATE = WIKI_TRANSFORMATION + "/{id}/aggregate";

    /** Wiki 转换运行详情 */
    public static final String WIKI_TRANSFORMATION_RUN_BY_ID = WIKI_TRANSFORMATION + "/runs/{runId}";

    /** Wiki 转换运行列表 */
    public static final String WIKI_TRANSFORMATION_RUNS = WIKI_TRANSFORMATION + "/runs";

    /** Wiki 转换运行取消 */
    public static final String WIKI_TRANSFORMATION_RUN_CANCEL = WIKI_TRANSFORMATION + "/runs/{runId}/cancel";

    /** Wiki 转换运行保存为页面 */
    public static final String WIKI_TRANSFORMATION_RUN_SAVE_AS_PAGE = WIKI_TRANSFORMATION + "/runs/{runId}/save-as-page";

    /** Wiki 转换运行删除 */
    public static final String WIKI_TRANSFORMATION_RUN_DELETE = WIKI_TRANSFORMATION + "/runs/{runId}";

    // ==================== Wiki 关联模块 ====================

    /** Wiki 关联页面 */
    public static final String WIKI_RELATED_PAGES = WIKI + "/kb/{kbId}/pages/{slug}/related";

    /** Wiki 关联解释 */
    public static final String WIKI_RELATION_EXPLAIN = WIKI + "/kb/{kbId}/pages/{slugA}/relation/{slugB}";

    /** Wiki 原始材料关联页面 */
    public static final String WIKI_RAW_PAGES = WIKI + "/raw/{rawId}/pages";

    /** Wiki 分块关联页面 */
    public static final String WIKI_CHUNK_PAGES = WIKI + "/chunks/{chunkId}/pages";

    /** Wiki 页面引用 */
    public static final String WIKI_PAGE_CITATIONS = WIKI + "/kb/{kbId}/pages/{pageId}/citations";

    /** Wiki 处理任务 */
    public static final String WIKI_KB_JOBS = WIKI + "/kb/{kbId}/jobs";

    /** Wiki 统计 */
    public static final String WIKI_KB_STATS = WIKI + "/kb/{kbId}/stats";

    /** Wiki 页面增强 */
    public static final String WIKI_PAGE_ENRICH = WIKI + "/kb/{kbId}/pages/{slug}/enrich";

    /** Wiki 页面修复 */
    public static final String WIKI_PAGE_REPAIR = WIKI + "/kb/{kbId}/pages/{slug}/repair";

    /** Wiki 搜索预览 */
    public static final String WIKI_SEARCH_PREVIEW = WIKI + "/kb/{kbId}/search-preview";

    // ==================== Wiki 热缓存模块 ====================

    /** Wiki 热缓存基础路径 */
    public static final String WIKI_HOT_CACHE = WIKI + "/hot-cache";

    /** Wiki 热缓存详情 */
    public static final String WIKI_HOT_CACHE_BY_ID = WIKI_HOT_CACHE + "/{kbId}";

    /** Wiki 热缓存重新生成 */
    public static final String WIKI_HOT_CACHE_REGENERATE = WIKI_HOT_CACHE + "/{kbId}/regenerate";

    // ==================== Wiki 研究模块 ====================

    /** Wiki 研究基础路径 */
    public static final String WIKI_RESEARCH = WIKI + "/research";

    /** Wiki 研究开始 */
    public static final String WIKI_RESEARCH_START = WIKI_RESEARCH + "/start";

    /** Wiki 研究 SSE 流 */
    public static final String WIKI_RESEARCH_STREAM = WIKI_RESEARCH + "/stream/{sessionId}";

    // ==================== 触发器模块 ====================

    /** 触发器基础路径 */
    public static final String TRIGGER = API_V1 + "/triggers";

    /** 触发器详情/更新/删除 */
    public static final String TRIGGER_BY_ID = TRIGGER + "/{id}";

    /** 触发器事件注入 */
    public static final String TRIGGER_EVENTS = TRIGGER + "/events";

    // ==================== 生成文件模块 ====================

    /** 生成文件基础路径 */
    public static final String GENERATED_FILE = API_V1 + "/files/generated";

    /** 生成文件下载 */
    public static final String GENERATED_FILE_BY_ID = GENERATED_FILE + "/{id}";

    // ==================== 安全模块 ====================

    /** 安全基础路径 */
    public static final String SECURITY = API_V1 + "/security";

    /** 安全防护配置 */
    public static final String SECURITY_GUARD_CONFIG = SECURITY + "/guard/config";

    /** 安全文件防护配置 */
    public static final String SECURITY_FILE_GUARD_CONFIG = SECURITY + "/guard/config/file-guard";

    /** 安全防护规则列表 */
    public static final String SECURITY_GUARD_RULES = SECURITY + "/guard/rules";

    /** 安全防护内置规则 */
    public static final String SECURITY_GUARD_RULES_BUILTIN = SECURITY + "/guard/rules/builtin";

    /** 安全防护规则详情/更新/删除 */
    public static final String SECURITY_GUARD_RULE_BY_ID = SECURITY + "/guard/rules/{ruleId}";

    /** 安全防护规则切换 */
    public static final String SECURITY_GUARD_RULE_TOGGLE = SECURITY + "/guard/rules/{ruleId}/toggle";

    /** 安全防护规则按主键删除 */
    public static final String SECURITY_GUARD_RULE_BY_PK = SECURITY + "/guard/rules/by-id/{id}";

    /** 安全防护规则导出 */
    public static final String SECURITY_GUARD_RULES_EXPORT = SECURITY + "/guard/rules/export";

    /** 安全防护规则导入 */
    public static final String SECURITY_GUARD_RULES_IMPORT = SECURITY + "/guard/rules/import";

    /** 审计日志列表 */
    public static final String SECURITY_AUDIT_LOGS = SECURITY + "/audit/logs";

    /** 审计统计 */
    public static final String SECURITY_AUDIT_STATS = SECURITY + "/audit/stats";

    /** 安全审批列表 */
    public static final String SECURITY_APPROVALS = SECURITY + "/approvals";

    // ==================== 功能开关模块 ====================

    /** 功能开关基础路径 */
    public static final String FEATURE_FLAG = API_V1 + "/feature-flags";

    /** 功能开关更新 */
    public static final String FEATURE_FLAG_BY_KEY = FEATURE_FLAG + "/{flagKey}";

    // ==================== 技能模板模块 ====================

    /** 技能模板基础路径 */
    public static final String SKILL_TEMPLATE = API_V1 + "/skill-templates";

    /** 技能模板详情 */
    public static final String SKILL_TEMPLATE_BY_ID = SKILL_TEMPLATE + "/{id}";

    /** 技能模板实例化 */
    public static final String SKILL_TEMPLATE_INSTANTIATE = SKILL_TEMPLATE + "/{id}/instantiate";

    // ==================== 技能密钥模块 ====================

    /** 技能密钥基础路径 */
    public static final String SKILL_SECRET = API_V1 + "/skills/{skillId}/secrets";

    /** 技能密钥删除 */
    public static final String SKILL_SECRET_BY_KEY = API_V1 + "/skills/{skillId}/secrets/{key}";

    // ==================== Agent 运行时管理模块 ====================

    /** Agent 运行时基础路径 */
    public static final String AGENT_RUNTIME = API_V1 + "/admin/agent-runtime";

    /** Agent 运行时快照 */
    public static final String AGENT_RUNTIME_SNAPSHOT = AGENT_RUNTIME + "/snapshot";

    /** Agent 运行时停止 */
    public static final String AGENT_RUNTIME_STOP = AGENT_RUNTIME + "/runs/{conversationId}/stop";

    /** Agent 运行时回收 */
    public static final String AGENT_RUNTIME_RECYCLE = AGENT_RUNTIME + "/runs/{conversationId}/recycle";

    /** Agent 运行时子Agent中断 */
    public static final String AGENT_RUNTIME_SUBAGENT_INTERRUPT = AGENT_RUNTIME + "/subagents/{subagentId}/interrupt";

    /** Agent 运行时清理 */
    public static final String AGENT_RUNTIME_SWEEP = AGENT_RUNTIME + "/sweep";

    // ==================== 活动流模块 ====================

    /** 活动流基础路径 */
    public static final String ACTIVITY = API_V1 + "/activity";

    /** 活动流 */
    public static final String ACTIVITY_FEED = ACTIVITY + "/feed";

    // ==================== TTS 语音合成模块 ====================

    /** TTS 基础路径 */
    public static final String TTS = API_V1 + "/tts";

    /** TTS 合成 */
    public static final String TTS_SYNTHESIZE = TTS + "/synthesize";

    /** TTS 语音列表 */
    public static final String TTS_VOICES = TTS + "/voices";

    // ==================== STT 语音识别模块 ====================

    /** STT 基础路径 */
    public static final String STT = API_V1 + "/stt";

    /** STT 转录 */
    public static final String STT_TRANSCRIBE = STT + "/transcribe";

    // ==================== 审计事件模块 ====================

    /** 审计事件基础路径 */
    public static final String AUDIT = API_V1 + "/audit";

    /** 审计事件列表 */
    public static final String AUDIT_EVENTS = AUDIT + "/events";

    // ==================== 个人访问令牌模块 ====================

    /** 个人访问令牌基础路径 */
    public static final String AUTH_TOKEN = AUTH + "/tokens";

    /** 个人访问令牌详情/删除 */
    public static final String AUTH_TOKEN_BY_ID = AUTH + "/tokens/{id}";

    // ==================== Agent 绑定模块 ====================

    /** Agent Skill 绑定列表 */
    public static final String AGENT_SKILLS = AGENT + "/{agentId}/skills";

    /** Agent 单个 Skill 绑定 */
    public static final String AGENT_SKILL_BY_ID = AGENT + "/{agentId}/skills/{skillId}";

    /** Agent Tool 绑定列表 */
    public static final String AGENT_TOOLS = AGENT + "/{agentId}/tools";

    /** Agent Provider 偏好 */
    public static final String AGENT_PROVIDER_PREFERENCES = AGENT + "/{agentId}/provider-preferences";

    /** Agent SSE 流式对话 */
    public static final String AGENT_CHAT_STREAM = AGENT + "/{id}/chat/stream";

    /** Agent 记忆导出 */
    public static final String AGENT_MEMORY_EXPORT = AGENT + "/{agentId}/workspace/memory/export";

    /** Agent 记忆导入预览 */
    public static final String AGENT_MEMORY_IMPORT_PREVIEW = AGENT + "/{agentId}/workspace/memory/import/preview";

    /** Agent 记忆导入 */
    public static final String AGENT_MEMORY_IMPORT = AGENT + "/{agentId}/workspace/memory/import";

    // ==================== 模板模块 ====================

    /** 模板基础路径 */
    public static final String TEMPLATE = API_V1 + "/templates";

    /** 模板应用 */
    public static final String TEMPLATE_APPLY = TEMPLATE + "/{id}/apply";

    // ==================== Dream 梦境模块 ====================

    /** Dream 基础路径 */
    public static final String DREAM = API_V1 + "/memory/{agentId}/dream";

    /** Dream 报告列表 */
    public static final String DREAM_REPORTS = DREAM + "/reports";

    /** Dream 报告详情 */
    public static final String DREAM_REPORT_BY_ID = DREAM + "/reports/{reportId}";

    /** Dream 事件 SSE */
    public static final String DREAM_EVENTS = DREAM + "/events";

    /** Morning Card */
    public static final String DREAM_MORNING_CARD = DREAM + "/morning-card";

    /** Morning Card 已读 */
    public static final String DREAM_MORNING_CARD_SEEN = DREAM + "/morning-card/seen";

    /** Dream 条目确认 */
    public static final String DREAM_ENTRY_CONFIRM = DREAM + "/reports/{reportId}/entries/{key}/confirm";

    /** Dream 条目编辑 */
    public static final String DREAM_ENTRY_EDIT = DREAM + "/reports/{reportId}/entries/{key}/edit";

    // ==================== Fact 事实模块 ====================

    /** Fact 基础路径 */
    public static final String FACT = API_V1 + "/memory/{agentId}/facts";

    /** Fact 遗忘 */
    public static final String FACT_FORGET = FACT + "/{factId}/forget";

    /** Fact 反馈 */
    public static final String FACT_FEEDBACK = FACT + "/{factId}/feedback";

    /** Fact 矛盾列表 */
    public static final String FACT_CONTRADICTIONS = FACT + "/contradictions";

    /** Fact 矛盾解决 */
    public static final String FACT_CONTRADICTION_RESOLVE = FACT + "/contradictions/{contradictionId}/resolve";

    // ==================== OAuth 模块 ====================

    /** OpenAI OAuth 基础路径 */
    public static final String OAUTH_OPENAI = API_V1 + "/oauth/openai";

    /** OpenAI OAuth 授权 */
    public static final String OAUTH_OPENAI_AUTHORIZE = OAUTH_OPENAI + "/authorize";

    /** OpenAI OAuth 回调粘贴 */
    public static final String OAUTH_OPENAI_CALLBACK_PASTE = OAUTH_OPENAI + "/callback-paste";

    /** OpenAI Device Flow 开始 */
    public static final String OAUTH_OPENAI_DEVICE_START = OAUTH_OPENAI + "/device/start";

    /** OpenAI Device Flow 轮询 */
    public static final String OAUTH_OPENAI_DEVICE_POLL = OAUTH_OPENAI + "/device/poll";

    /** OpenAI Device Flow 取消 */
    public static final String OAUTH_OPENAI_DEVICE_CANCEL = OAUTH_OPENAI + "/device/cancel";

    /** OpenAI OAuth 刷新 */
    public static final String OAUTH_OPENAI_REFRESH = OAUTH_OPENAI + "/refresh";

    /** OpenAI OAuth 撤销 */
    public static final String OAUTH_OPENAI_REVOKE = OAUTH_OPENAI + "/revoke";

    /** OpenAI OAuth 状态 */
    public static final String OAUTH_OPENAI_STATUS = OAUTH_OPENAI + "/status";

    /** Anthropic OAuth 基础路径 */
    public static final String OAUTH_ANTHROPIC = API_V1 + "/oauth/anthropic";

    /** Anthropic OAuth 状态 */
    public static final String OAUTH_ANTHROPIC_STATUS = OAUTH_ANTHROPIC + "/status";

    /** Anthropic OAuth 重载 */
    public static final String OAUTH_ANTHROPIC_RELOAD = OAUTH_ANTHROPIC + "/reload";

    // ==================== ACP 端点模块 ====================

    /** ACP 端点基础路径 */
    public static final String ACP_ENDPOINT = API_V1 + "/acp/endpoints";

    /** ACP 端点详情/更新/删除 */
    public static final String ACP_ENDPOINT_BY_ID = ACP_ENDPOINT + "/{id}";

    /** ACP 端点切换 */
    public static final String ACP_ENDPOINT_TOGGLE = ACP_ENDPOINT + "/{id}/toggle";

    /** ACP 端点测试 */
    public static final String ACP_ENDPOINT_TEST = ACP_ENDPOINT + "/{id}/test";

    // ==================== 插件模块 ====================

    /** 插件基础路径 */
    public static final String PLUGIN = API_V1 + "/plugins";

    /** 插件详情 */
    public static final String PLUGIN_BY_NAME = PLUGIN + "/{name}";

    /** 插件禁用 */
    public static final String PLUGIN_DISABLE = PLUGIN + "/{name}/disable";

    /** 插件启用 */
    public static final String PLUGIN_ENABLE = PLUGIN + "/{name}/enable";

    /** 插件配置 */
    public static final String PLUGIN_CONFIG = PLUGIN + "/{name}/config";

    // ==================== 计划模块 ====================

    /** 计划基础路径 */
    public static final String PLAN = API_V1 + "/plans";

    /** 计划详情 */
    public static final String PLAN_BY_ID = PLAN + "/{id}";

    // ==================== 通知模块 ====================

    /** 通知摘要 */
    public static final String NOTIFICATION_SUMMARY = API_V1 + "/notifications/summary";

    // ==================== 系统健康模块 ====================

    /** 系统健康检查 */
    public static final String SYSTEM_HEALTH = API_V1 + "/system/health";

    /** 浏览器健康诊断 */
    public static final String SYSTEM_BROWSER_HEALTH = API_V1 + "/system/browser-health";

    // ==================== 子Agent模块 ====================

    /** 子Agent基础路径 */
    public static final String SUBAGENT = API_V1 + "/subagents";

    /** 子Agent中断 */
    public static final String SUBAGENT_INTERRUPT = SUBAGENT + "/{subagentId}/interrupt";

    /** 子Agent暂停生成 */
    public static final String SUBAGENT_SPAWN_PAUSE = SUBAGENT + "/spawn-pause";

    /** 活跃子Agent列表 */
    public static final String SUBAGENT_ACTIVE = SUBAGENT + "/active";

    // ==================== 技能安装模块 ====================

    /** 技能安装基础路径 */
    public static final String SKILL_INSTALL = SKILL + "/install";

    /** ClawHub 搜索 */
    public static final String SKILL_INSTALL_HUB_SEARCH = SKILL_INSTALL + "/hub/search";

    /** 开始安装 */
    public static final String SKILL_INSTALL_START = SKILL_INSTALL + "/start";

    /** 安装状态 */
    public static final String SKILL_INSTALL_STATUS = SKILL_INSTALL + "/status/{taskId}";

    /** 取消安装 */
    public static final String SKILL_INSTALL_CANCEL = SKILL_INSTALL + "/cancel/{taskId}";

    /** 上传安装 */
    public static final String SKILL_INSTALL_UPLOAD = SKILL_INSTALL + "/upload";

    /** 卸载技能 */
    public static final String SKILL_INSTALL_BY_NAME = SKILL_INSTALL + "/{skillName}";

    // ==================== 会话消息清空 ====================

    /** 会话消息清空 */
    public static final String CONVERSATION_MESSAGES_CLEAR = CONVERSATION + "/{conversationId}/messages";
}
