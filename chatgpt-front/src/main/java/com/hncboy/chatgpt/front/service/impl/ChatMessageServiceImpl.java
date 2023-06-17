package com.hncboy.chatgpt.front.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hncboy.chatgpt.base.config.ChatConfig;
import com.hncboy.chatgpt.base.domain.entity.ChatMessageDO;
import com.hncboy.chatgpt.base.domain.entity.ChatRoomDO;
import com.hncboy.chatgpt.base.enums.ApiTypeEnum;
import com.hncboy.chatgpt.base.enums.ChatMessageStatusEnum;
import com.hncboy.chatgpt.base.enums.ChatMessageTypeEnum;
import com.hncboy.chatgpt.base.exception.ServiceException;
import com.hncboy.chatgpt.base.mapper.ChatMessageMapper;
import com.hncboy.chatgpt.base.util.ObjectMapperUtil;
import com.hncboy.chatgpt.base.util.WebUtil;
import com.hncboy.chatgpt.front.domain.request.ChatProcessRequest;
import com.hncboy.chatgpt.front.handler.emitter.ChatMessageEmitterChain;
import com.hncboy.chatgpt.front.handler.emitter.IpRateLimiterEmitterChain;
import com.hncboy.chatgpt.front.handler.emitter.ResponseEmitterChain;
import com.hncboy.chatgpt.front.handler.emitter.SensitiveWordEmitterChain;
import com.hncboy.chatgpt.front.service.ChatMessageService;
import com.hncboy.chatgpt.front.service.ChatRoomService;
import com.hncboy.chatgpt.front.util.FrontUserUtil;
import com.unfbx.chatgpt.entity.chat.Message;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * @author hncboy
 * @date 2023-3-25 聊天记录相关业务实现类
 */
@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageDO>
		implements ChatMessageService {

	@Resource
	private ChatConfig chatConfig;

	@Resource
	private ChatRoomService chatRoomService;

	@Override
	public ResponseBodyEmitter sendMessage(ChatProcessRequest chatProcessRequest) {
		// 超时时间设置 3 分钟
		ResponseBodyEmitter emitter = new ResponseBodyEmitter(3 * 60 * 1000L);
		emitter.onCompletion(() -> log.debug("请求参数：{}，Front-end closed the emitter connection.",
				ObjectMapperUtil.toJson(chatProcessRequest)));
		emitter.onTimeout(() -> log.error("请求参数：{}，Back-end closed the emitter connection.",
				ObjectMapperUtil.toJson(chatProcessRequest)));

		// 构建 emitter 处理链路
		ResponseEmitterChain ipRateLimiterEmitterChain = new IpRateLimiterEmitterChain();
		ResponseEmitterChain sensitiveWordEmitterChain = new SensitiveWordEmitterChain();
		sensitiveWordEmitterChain.setNext(new ChatMessageEmitterChain());
		ipRateLimiterEmitterChain.setNext(sensitiveWordEmitterChain);
		ipRateLimiterEmitterChain.doChain(chatProcessRequest, emitter);
		return emitter;
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public ChatMessageDO initChatMessage(ChatProcessRequest chatProcessRequest, ApiTypeEnum apiTypeEnum) {
		ChatMessageDO chatMessageDO = new ChatMessageDO();
		chatMessageDO.setId(IdWorker.getId());
		// 消息 id 手动生成
		chatMessageDO.setMessageId(UUID.randomUUID().toString());
		chatMessageDO.setMessageType(ChatMessageTypeEnum.QUESTION);
		chatMessageDO.setApiType(apiTypeEnum);
		if (apiTypeEnum == ApiTypeEnum.API_KEY) {
			chatMessageDO.setApiKey(chatConfig.getOpenaiApiKey());
		}
		chatMessageDO.setUserId(FrontUserUtil.getUserId());
		chatMessageDO.setContent(chatProcessRequest.getPrompt());
		chatMessageDO.setModelName(chatConfig.getOpenaiApiModel());
		chatMessageDO.setOriginalData(null);
		chatMessageDO.setPromptTokens(-1);
		chatMessageDO.setCompletionTokens(-1);
		chatMessageDO.setTotalTokens(-1);
		chatMessageDO.setIp(WebUtil.getIp());
		chatMessageDO.setStatus(ChatMessageStatusEnum.INIT);
		chatMessageDO.setCreateTime(new Date());
		chatMessageDO.setUpdateTime(new Date());

		// 填充初始化父级消息参数
		populateInitParentMessage(chatMessageDO, chatProcessRequest);

		save(chatMessageDO);
		return chatMessageDO;
	}

	/**
	 * 填充初始化父级消息参数
	 *
	 * @param chatMessageDO      消息记录
	 * @param chatProcessRequest 消息处理请求参数
	 */
	private void populateInitParentMessage(ChatMessageDO chatMessageDO, ChatProcessRequest chatProcessRequest) {
		// 父级消息 id
		String parentMessageId = Optional.ofNullable(chatProcessRequest.getOptions())
				.map(ChatProcessRequest.Options::getParentMessageId).orElse(null);

		// 对话 id
		String conversationId = Optional.ofNullable(chatProcessRequest.getOptions())
				.map(ChatProcessRequest.Options::getConversationId).orElse(null);

		if (StrUtil.isAllNotBlank(parentMessageId, conversationId)) {
			// 寻找父级消息
			ChatMessageDO parentChatMessage = getOne(new LambdaQueryWrapper<ChatMessageDO>()
					// 用户 id 一致
					.eq(ChatMessageDO::getUserId, FrontUserUtil.getUserId())
					// 消息 id 一致
					.eq(ChatMessageDO::getMessageId, parentMessageId)
					// 对话 id 一致
					.eq(ChatMessageDO::getConversationId, conversationId)
					// Api 类型一致
					.eq(ChatMessageDO::getApiType, chatMessageDO.getApiType())
					// 消息类型为回答
					.eq(ChatMessageDO::getMessageType, ChatMessageTypeEnum.ANSWER));
			if (Objects.isNull(parentChatMessage)) {
				throw new ServiceException("父级消息不存在，本次对话出错，请先关闭上下文或开启新的对话窗口");
			}

			chatMessageDO.setParentMessageId(parentMessageId);
			chatMessageDO.setParentAnswerMessageId(parentMessageId);
			chatMessageDO.setParentQuestionMessageId(parentChatMessage.getParentQuestionMessageId());
			chatMessageDO.setChatRoomId(parentChatMessage.getChatRoomId());
			chatMessageDO.setConversationId(parentChatMessage.getConversationId());
			chatMessageDO.setContextCount(parentChatMessage.getContextCount() + 1);
			chatMessageDO.setQuestionContextCount(parentChatMessage.getQuestionContextCount() + 1);

			if (chatMessageDO.getApiType() == ApiTypeEnum.ACCESS_TOKEN) {
				if (!Objects.equals(chatMessageDO.getModelName(), parentChatMessage.getModelName())) {
					throw new ServiceException(StrUtil.format("当前对话类型为 AccessToken 使用模型不一样，请开启新的对话"));
				}
			}

			// ApiKey 限制上下文问题的数量
			if (chatMessageDO.getApiType() == ApiTypeEnum.API_KEY && chatConfig.getLimitQuestionContextCount() > 0
					&& chatMessageDO.getQuestionContextCount() > chatConfig.getLimitQuestionContextCount()) {
				throw new ServiceException(StrUtil.format("当前允许连续对话的问题数量为[{}]次，已达到上限，请关闭上下文对话重新发送",
						chatConfig.getLimitQuestionContextCount()));
			}
		} else {
			// 创建新聊天室
			ChatRoomDO chatRoomDO = chatRoomService.createChatRoom(chatMessageDO);
			chatMessageDO.setChatRoomId(chatRoomDO.getId());
			chatMessageDO.setContextCount(1);
			chatMessageDO.setQuestionContextCount(1);
		}
	}

	@Override
	@Async
	public void summary(long id, LinkedList<Message> messages) {
		List<Message> dataList = new ArrayList<>();
		for (int i = 0; i < messages.size() - 1; i++) {
			dataList.add(messages.get(i));
		}
		dataList.add(new Message(Message.Role.USER.getName(), "请对所有历史对话涉及到职业指导、职业测评的内容进行总结", ""));
		JSONObject data = new JSONObject();
		data.put("model", "gpt-4");
		data.put("messages", dataList);
		System.out.println(data.toString());
		// 构建HTTP请求
		HttpRequest request = HttpRequest.post("https://bytetop.cn/chat-api/chat")
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer 8DC3825E88DA8026DE87EA34AE0CD3EB").body(data.toString());

		// 发送HTTP请求
		HttpResponse response = request.execute();

		// 获取响应状态码和响应内容
		int statusCode = response.getStatus();
		String responseBody = response.body();
		// 打印响应结果
		System.out.println("总结uStatus Code: " + statusCode);
		System.out.println("总结: " + responseBody);
		JSONObject result = new JSONObject(responseBody);
		String content = result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getStr("content");
		System.out.println("总结: " + content);
		// 将content设置到最新的message里
		ChatMessageDO chatMessageDO = new ChatMessageDO();
		chatMessageDO.setId(id);
		chatMessageDO.setSummary(content);
		baseMapper.updateById(chatMessageDO);
	}
}
