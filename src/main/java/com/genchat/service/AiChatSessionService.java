package com.genchat.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genchat.common.AgentResponse;
import com.genchat.converter.AiChatSessionConverter;
import com.genchat.dto.AiChatSession;
import com.genchat.entity.AgentState;
import com.genchat.entity.AiChatSessionEntity;
import com.genchat.repository.AiChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

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

    /**
     * Query session by id
     */
    public Optional<AiChatSession> queryById(Long id) {
        return Optional.ofNullable(getById(id))
                .map(AiChatSessionConverter.INSTANCE::toDto);
    }

    /**
     * Update session by id
     */
    public void updateSession(AiChatSession session) {
        var entity = AiChatSessionConverter.INSTANCE.toEntity(session);
        updateById(entity);
    }

    public void update(Long id,
                       StringBuilder finalAnswerBuffer,
                       StringBuilder thinkingBuffer,
                       AgentState agentState,
                       long totalResponseTime,
                       long fistResponseTime,
                       String usedToolsString,
                       String currentRecommendations,
                       String agentType) {
        String referenceJson = "";
        if (!agentState.searchResults.isEmpty()) {
            referenceJson = AgentResponse.reference(JSON.toJSONString(agentState.searchResults));
        }
        var wrapper = new LambdaUpdateWrapper<AiChatSessionEntity>();
        wrapper.eq(AiChatSessionEntity::getId, id)
                .set(AiChatSessionEntity::getAnswer, finalAnswerBuffer.toString())
                .set(StringUtils.hasLength(thinkingBuffer.toString()), AiChatSessionEntity::getThinking, thinkingBuffer.toString())
                .set(StringUtils.hasLength(usedToolsString), AiChatSessionEntity::getTools, usedToolsString)
                .set(StringUtils.hasLength(referenceJson), AiChatSessionEntity::getReference, referenceJson)
                .set(StringUtils.hasLength(currentRecommendations), AiChatSessionEntity::getRecommend, currentRecommendations)
                .set(StringUtils.hasLength(agentType), AiChatSessionEntity::getAgentType, agentType)
                .set(AiChatSessionEntity::getTotalResponseTime, totalResponseTime)
                .set(AiChatSessionEntity::getFirstResponseTime, fistResponseTime);
        update(wrapper);
    }
}
