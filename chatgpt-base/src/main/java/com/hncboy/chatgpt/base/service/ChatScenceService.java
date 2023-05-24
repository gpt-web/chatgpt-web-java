package com.hncboy.chatgpt.base.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hncboy.chatgpt.base.domain.entity.ChatScenceDO;

/**
 * @author hncboy
 * @date 2023-3-27
 * 聊天场景相关业务接口
 */
public interface ChatScenceService extends IService<ChatScenceDO> {


	ChatScenceDO getScence();
}
