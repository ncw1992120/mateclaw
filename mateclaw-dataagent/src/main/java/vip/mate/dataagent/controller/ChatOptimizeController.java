package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.common.result.R;
import vip.mate.dataagent.dto.OptimizeRequest;
import vip.mate.dataagent.dto.OptimizeResponse;
import vip.mate.dataagent.service.ChatOptimizeService;

/**
 * 对话输入优化控制器
 */
@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
@Tag(name = "对话输入优化", description = "对话输入文本润色优化接口")
public class ChatOptimizeController {

    private final ChatOptimizeService chatOptimizeService;

    @PostMapping("/optimize")
    @Operation(summary = "优化输入内容", description = "调用 LLM 对用户输入的文本进行润色优化，使其更清晰、更专业、更有条理")
    public R<OptimizeResponse> optimize(@RequestBody OptimizeRequest request) {
        String optimized = chatOptimizeService.optimizePrompt(request.getInput());
        OptimizeResponse response = new OptimizeResponse();
        response.setOptimized(optimized);
        return R.ok(response);
    }
}
