package com.hncboy.chatgpt.base.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hncboy.chatgpt.base.domain.entity.ChatScenceDO;
import com.hncboy.chatgpt.base.mapper.ChatScenceMapper;
import com.hncboy.chatgpt.base.service.ChatScenceService;

/**
 * @author hncboy
 * @date 2023-3-27
 * 聊天室业务实现类
 */
@Service
public class ChatScenceServiceImpl extends ServiceImpl<ChatScenceMapper, ChatScenceDO> implements ChatScenceService {

	@Override
	public ChatScenceDO getScence() {
		return getById(1);
	}

}
