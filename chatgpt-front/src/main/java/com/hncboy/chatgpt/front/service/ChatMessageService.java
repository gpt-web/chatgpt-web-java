package com.hncboy.chatgpt.front.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hncboy.chatgpt.base.domain.entity.ChatMessageDO;
import com.hncboy.chatgpt.base.enums.ApiTypeEnum;
import com.hncboy.chatgpt.front.domain.request.ChatProcessRequest;
import com.unfbx.chatgpt.entity.chat.Message;

import java.util.LinkedList;

import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * @author hncboy
 * @date 2023-3-25 聊天记录相关业务接口
 */
public interface ChatMessageService extends IService<ChatMessageDO> {

	/**
	 * 消息处理
	 *
	 * @param chatProcessRequest 消息处理请求参数
	 * @return emitter
	 */
	ResponseBodyEmitter sendMessage(ChatProcessRequest chatProcessRequest);

	/**
	 * 初始化聊天消息
	 *
	 * @param chatProcessRequest 消息处理请求参数
	 * @param apiTypeEnum        API 类型
	 * @return 聊天消息
	 */
	ChatMessageDO initChatMessage(ChatProcessRequest chatProcessRequest, ApiTypeEnum apiTypeEnum);

	/**
	 * 总结对话
	 * 
	 * @param id
	 * @param messages
	 */
	void summary(long id, LinkedList<Message> messages);
}
