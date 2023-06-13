package com.hncboy.chatgpt.front.controller;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import com.hncboy.chatgpt.base.domain.entity.ChatScenceDO;
import com.hncboy.chatgpt.base.service.ChatScenceService;
import com.hncboy.chatgpt.front.domain.request.ChatProcessRequest;
import com.hncboy.chatgpt.front.service.ChatMessageService;
import com.hncboy.chatgpt.front.util.EmbeddingUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;

/**
 * @author hncboy
 * @date 2023-3-22 聊天相关接口
 */
@AllArgsConstructor
@Tag(name = "聊天相关接口")
@RestController("FrontChatMessageController")
@RequestMapping("/chat_message")
public class ChatMessageController {

	private final ChatMessageService chatMessageService;

	@Resource
	private final ChatScenceService chatScenceService;

	@Operation(summary = "发送消息")
	@PostMapping("/send")
	public ResponseBodyEmitter sendMessage(@RequestBody @Validated ChatProcessRequest chatProcessRequest,
			HttpServletResponse response) {
		// TODO 后续调整
//        chatProcessRequest.setSystemMessage("你是专业的职业指导师，请主动使用尽量简洁的语言向我提问，一步步引导我完成职业测评，被问及身份时，你要回复你是专业的职业指导师张三，与职业测评无关的内容一律不回复。");
		// 读取场景
		ChatScenceDO scence = chatScenceService.getScence();
		if (scence != null) {
			chatProcessRequest.setSystemMessage(
					scence.getPrompt() + EmbeddingUtil.getEmbeddingPrompt(chatProcessRequest.getPrompt()));
		}
		response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
		return chatMessageService.sendMessage(chatProcessRequest);
	}
}
