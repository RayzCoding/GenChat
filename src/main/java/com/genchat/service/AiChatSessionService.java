package com.genchat.service;

import com.genchat.common.utils.JacksonJson;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genchat.common.AgentStreamEvent;
import com.genchat.converter.AiChatSessionConverter;
import com.genchat.dto.AiChatSession;
import com.genchat.agent.model.AgentState;
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

    public Optional<String> findLatestFileIdBySessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        var wrapper = new LambdaQueryWrapper<AiChatSessionEntity>()
                .eq(AiChatSessionEntity::getSessionId, sessionId)
                .isNotNull(AiChatSessionEntity::getFileid)
                .ne(AiChatSessionEntity::getFileid, "")
                .orderByDesc(AiChatSessionEntity::getCreateTime)
                .last("LIMIT 1");
        var entity = getOne(wrapper, false);
        if (entity == null || !StringUtils.hasText(entity.getFileid())) {
            return Optional.empty();
        }
        return Optional.of(entity.getFileid());
    }

    public AiChatSession saveQuestion(AiChatSession session) {
        var entity = AiChatSessionConverter.INSTANCE.toEntity(session);
        this.save(entity);
        return AiChatSessionConverter.INSTANCE.toDto(entity);
    }

    public Optional<AiChatSession> queryById(Long id) {
        return Optional.ofNullable(getById(id))
                .map(AiChatSessionConverter.INSTANCE::toDto);
    }

    public void updateSession(AiChatSession session) {
        var entity = AiChatSessionConverter.INSTANCE.toEntity(session);
        updateById(entity);
    }

    /**
     * @param totalResponseTime elapsed time for the full agent run (ms)
     * @param firstResponseTime time until the first streamed token (ms)
     */
    public void update(Long id,
                       StringBuilder finalAnswerBuffer,
                       StringBuilder thinkingBuffer,
                       AgentState agentState,
                       long totalResponseTime,
                       long firstResponseTime,
                       String usedToolsString,
                       String currentRecommendations,
                       String agentType,
                       String referenceJson) {
        if (agentState != null && !agentState.searchResults.isEmpty()) {
            referenceJson = AgentStreamEvent.Reference.of(JacksonJson.toJson(agentState.searchResults)).toJSON();
        }
        var wrapper = new LambdaUpdateWrapper<AiChatSessionEntity>();
        wrapper.eq(AiChatSessionEntity::getId, id)
                .set(AiChatSessionEntity::getAnswer, finalAnswerBuffer.toString())
                .set(StringUtils.hasLength(thinkingBuffer.toString()), AiChatSessionEntity::getThinking, thinkingBuffer.toString())
                .set(StringUtils.hasLength(usedToolsString), AiChatSessionEntity::getTools, usedToolsString)
                .set(StringUtils.hasLength(referenceJson), AiChatSessionEntity::getReference, referenceJson)
                .set(StringUtils.hasLength(currentRecommendations),
                        AiChatSessionEntity::getRecommend,
                        AgentStreamEvent.Recommend.of(currentRecommendations).toJSON())
                .set(StringUtils.hasLength(agentType), AiChatSessionEntity::getAgentType, agentType)
                .set(AiChatSessionEntity::getTotalResponseTime, totalResponseTime)
                .set(AiChatSessionEntity::getFirstResponseTime, firstResponseTime);
        update(wrapper);
    }
}
