package vip.mate.sdk.service.llm.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import vip.mate.exception.MateClawException;
import vip.mate.llm.chatmodel.ProviderChatModelFactory;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.sdk.service.llm.LlmRuntime;
import vip.mate.sdk.service.llm.dto.LlmChatMessage;
import vip.mate.sdk.service.llm.dto.LlmChatRequest;
import vip.mate.sdk.service.llm.dto.LlmChatResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 大模型直连运行时实现
 * <p>
 * 直接基于 {@link ProviderChatModelFactory} 构造 {@link ChatModel} 并调用，
 * 不经过 Agent / 会话 / 持久化链路。起源场景：例如外部调用方仅想"像调用大模型
 * HTTP API 一样"发送一段对话并拿到回答，而不希望产生任何会话记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmRuntimeImpl implements LlmRuntime {

    private final ModelConfigService modelConfigService;
    private final ProviderChatModelFactory chatModelFactory;
    private final RetryTemplate retryTemplate;

    @Override
    public LlmChatResponse chatDirect(LlmChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new MateClawException("err.llm.messages_required", "大模型直连请求的消息列表不能为空");
        }
        ModelConfigEntity config = resolveModel(request.getProvider(), request.getModel());
        ChatModel chatModel = chatModelFactory.buildFor(config, retryTemplate);
        Prompt prompt = buildPrompt(request);

        ChatResponse response = chatModel.call(prompt);

        LlmChatResponse result = new LlmChatResponse();
        result.setContent(response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText() : "");
        result.setProvider(config.getProvider());
        result.setModel(config.getModelName());
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            result.setPromptTokens(usage.getPromptTokens() != null ? usage.getPromptTokens() : 0);
            result.setCompletionTokens(usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0);
        }
        return result;
    }

    @Override
    public Flux<String> chatDirectStream(LlmChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return Flux.error(new MateClawException("err.llm.messages_required", "大模型直连请求的消息列表不能为空"));
        }
        ModelConfigEntity config = resolveModel(request.getProvider(), request.getModel());
        ChatModel chatModel = chatModelFactory.buildFor(config, retryTemplate);
        Prompt prompt = buildPrompt(request);

        return chatModel.stream(prompt)
                .map(response -> response.getResult() != null && response.getResult().getOutput() != null
                        ? response.getResult().getOutput().getText() : "")
                .filter(StringUtils::hasText);
    }

    /**
     * 解析目标模型配置：
     * <ul>
     *   <li>provider + model 均指定 → 精确匹配已启用模型，找不到则抛错；</li>
     *   <li>仅指定 model → 按名称解析（支持回退默认模型）；</li>
     *   <li>均未指定 → 使用默认模型。</li>
     * </ul>
     */
    private ModelConfigEntity resolveModel(String provider, String model) {
        ModelConfigEntity config;
        if (StringUtils.hasText(provider) && StringUtils.hasText(model)) {
            config = modelConfigService.findEnabledModel(provider.trim(), model.trim());
            if (config == null) {
                throw new MateClawException("err.llm.model_not_enabled",
                        "模型未启用或不存在: " + provider + "/" + model);
            }
        } else if (StringUtils.hasText(model)) {
            config = modelConfigService.resolveModel(model.trim());
            if (config == null) {
                throw new MateClawException("err.llm.model_not_found", "模型不存在: " + model);
            }
        } else {
            config = modelConfigService.getDefaultModel();
            if (config == null) {
                throw new MateClawException("err.llm.no_default_model", "未配置默认模型");
            }
        }
        return config;
    }

    /**
     * 将请求消息列表转换为 Spring AI Prompt，并透传可选的 temperature / maxTokens。
     */
    private Prompt buildPrompt(LlmChatRequest request) {
        List<Message> messages = new ArrayList<>();
        for (LlmChatMessage msg : request.getMessages()) {
            if (msg == null || !StringUtils.hasText(msg.getContent())) {
                continue;
            }
            String role = msg.getRole() == null ? "user" : msg.getRole().trim().toLowerCase(Locale.ROOT);
            switch (role) {
                case "system" -> messages.add(new SystemMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                default -> messages.add(new UserMessage(msg.getContent()));
            }
        }
        if (messages.isEmpty()) {
            throw new MateClawException("err.llm.messages_required", "大模型直连请求的消息列表不能为空");
        }

        ChatOptions options = buildChatOptions(request);
        return options != null ? new Prompt(messages, options) : new Prompt(messages);
    }

    private ChatOptions buildChatOptions(LlmChatRequest request) {
        if (Objects.isNull(request.getTemperature()) && Objects.isNull(request.getMaxTokens())) {
            return null;
        }
        ChatOptions.Builder builder = ChatOptions.builder();
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        }
        return builder.build();
    }
}
