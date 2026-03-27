package com.genchat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genchat.converter.AiChatSessionConverter;
import com.genchat.dto.AiChatSession;
import com.genchat.entity.AiChatSessionEntity;
import com.genchat.repository.AiChatSessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiChatSessionService extends ServiceImpl<AiChatSessionRepository, AiChatSessionEntity> {

    public List<AiChatSession> queryRecentBySessionId(String sessionId, int maxMessages) {
        var wrapper = new LambdaQueryWrapper<AiChatSessionEntity>()
                .eq(AiChatSessionEntity::getSessionId, sessionId)
                .orderByAsc(AiChatSessionEntity::getCreateTime)
                .last("LIMIT " + maxMessages);

        var entities = list(wrapper);
        return AiChatSessionConverter.INSTANCE.toDtoList(entities);
    }

    public AiChatSession saveQuestion(AiChatSession session) {
        var entity = AiChatSessionConverter.INSTANCE.toEntity(session);
        this.save(entity);
        return AiChatSessionConverter.INSTANCE.toDto(entity);
    }
}
