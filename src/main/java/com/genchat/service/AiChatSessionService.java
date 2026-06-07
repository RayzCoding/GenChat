package com.genchat.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genchat.common.AgentResponse;
import com.genchat.converter.AiChatSessionConverter;
import com.genchat.dto.*;
import com.genchat.entity.AgentState;
import com.genchat.entity.AiChatSessionEntity;
import com.genchat.repository.AiChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AiChatSessionService extends ServiceImpl<AiChatSessionRepository, AiChatSessionEntity> {

    private static final int MAX_TITLE_LENGTH = 80;

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

    public Optional<AiChatSession> queryById(Long id) {
        return Optional.ofNullable(getById(id))
                .map(AiChatSessionConverter.INSTANCE::toDto);
    }

    public void updateSession(AiChatSession session) {
        var entity = AiChatSessionConverter.INSTANCE.toEntity(session);
        updateById(entity);
    }

    public PageResult<SessionSummaryDTO> listSessions(String agentType, int page, int pageSize) {
        return querySessionSummaries(agentType, null, page, pageSize);
    }

    public PageResult<SessionSummaryDTO> searchSessions(String agentType, String keyword, int page, int pageSize) {
        if (!StringUtils.hasLength(keyword)) {
            return listSessions(agentType, page, pageSize);
        }
        return querySessionSummaries(agentType, keyword.trim(), page, pageSize);
    }

    public Optional<SessionDetailDTO> getSessionDetail(String conversationId) {
        var wrapper = new LambdaQueryWrapper<AiChatSessionEntity>()
                .eq(AiChatSessionEntity::getSessionId, conversationId)
                .orderByAsc(AiChatSessionEntity::getCreateTime);

        var entities = list(wrapper);
        if (entities.isEmpty()) {
            return Optional.empty();
        }

        var messages = entities.stream()
                .map(this::toMessageDto)
                .toList();

        return Optional.of(SessionDetailDTO.builder()
                .conversationId(conversationId)
                .messages(messages)
                .build());
    }

    private PageResult<SessionSummaryDTO> querySessionSummaries(String agentType,
                                                                String keyword,
                                                                int page,
                                                                int pageSize) {
        var safePage = Math.max(page, 1);
        var safePageSize = Math.min(Math.max(pageSize, 1), 100);

        var total = countDistinctSessions(agentType, keyword);

        var groupWrapper = buildGroupQueryWrapper(agentType, keyword);
        var mpPage = new Page<Map<String, Object>>(safePage, safePageSize);
        mpPage.setSearchCount(false);

        var pageResult = baseMapper.selectMapsPage(mpPage, groupWrapper);
        var sessionIds = pageResult.getRecords().stream()
                .map(row -> (String) row.get("session_id"))
                .toList();

        var firstQuestions = loadFirstQuestions(sessionIds);
        var lastQuestions = loadLastQuestions(sessionIds);

        var items = pageResult.getRecords().stream()
                .map(row -> toSummaryDto(row, firstQuestions, lastQuestions))
                .toList();

        return PageResult.<SessionSummaryDTO>builder()
                .total(total)
                .items(items)
                .build();
    }

    private QueryWrapper<AiChatSessionEntity> buildGroupQueryWrapper(String agentType, String keyword) {
        var wrapper = new QueryWrapper<AiChatSessionEntity>()
                .select("session_id",
                        "MAX(update_time) AS update_time",
                        "COUNT(*) AS message_count",
                        "MAX(agent_type) AS agent_type")
                .eq("agent_type", agentType)
                .groupBy("session_id")
                .orderByDesc("update_time");

        applyKeywordFilter(wrapper, keyword);
        return wrapper;
    }

    private long countDistinctSessions(String agentType, String keyword) {
        var countWrapper = new QueryWrapper<AiChatSessionEntity>()
                .select("COUNT(DISTINCT session_id) AS total")
                .eq("agent_type", agentType);
        applyKeywordFilter(countWrapper, keyword);

        var rows = baseMapper.selectMaps(countWrapper);
        if (rows.isEmpty()) {
            return 0L;
        }
        return toLong(rows.getFirst().get("total"));
    }

    private void applyKeywordFilter(QueryWrapper<AiChatSessionEntity> wrapper, String keyword) {
        if (StringUtils.hasLength(keyword)) {
            wrapper.and(w -> w.like("question", keyword).or().like("answer", keyword));
        }
    }

    private Map<String, String> loadFirstQuestions(List<String> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        var wrapper = new LambdaQueryWrapper<AiChatSessionEntity>()
                .in(AiChatSessionEntity::getSessionId, sessionIds)
                .select(AiChatSessionEntity::getSessionId, AiChatSessionEntity::getQuestion)
                .orderByAsc(AiChatSessionEntity::getCreateTime);

        var result = new LinkedHashMap<String, String>();
        for (var entity : list(wrapper)) {
            result.putIfAbsent(entity.getSessionId(), entity.getQuestion());
        }
        return result;
    }

    private Map<String, String> loadLastQuestions(List<String> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        var wrapper = new LambdaQueryWrapper<AiChatSessionEntity>()
                .in(AiChatSessionEntity::getSessionId, sessionIds)
                .select(AiChatSessionEntity::getSessionId, AiChatSessionEntity::getQuestion)
                .orderByDesc(AiChatSessionEntity::getCreateTime);

        var result = new LinkedHashMap<String, String>();
        for (var entity : list(wrapper)) {
            result.putIfAbsent(entity.getSessionId(), entity.getQuestion());
        }
        return result;
    }

    private SessionSummaryDTO toSummaryDto(Map<String, Object> row,
                                           Map<String, String> firstQuestions,
                                           Map<String, String> lastQuestions) {
        var sessionId = (String) row.get("session_id");
        var title = truncateTitle(firstQuestions.get(sessionId));

        return SessionSummaryDTO.builder()
                .conversationId(sessionId)
                .title(title)
                .lastQuestion(lastQuestions.get(sessionId))
                .messageCount(toInteger(row.get("message_count")))
                .agentType((String) row.get("agent_type"))
                .updatedAt(toLocalDateTime(row.get("update_time")))
                .build();
    }

    private String truncateTitle(String title) {
        if (!StringUtils.hasLength(title)) {
            return "";
        }
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, MAX_TITLE_LENGTH) + "...";
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private int toInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        return null;
    }

    private SessionMessageDTO toMessageDto(AiChatSessionEntity entity) {
        return SessionMessageDTO.builder()
                .id(entity.getId())
                .question(entity.getQuestion())
                .answer(entity.getAnswer())
                .thinking(entity.getThinking())
                .reference(parseReference(entity.getReference()))
                .recommend(parseRecommend(entity.getRecommend()))
                .createTime(entity.getCreateTime())
                .totalResponseTime(entity.getTotalResponseTime())
                .build();
    }

    private List<SearchResultDTO> parseReference(String referenceJson) {
        if (!StringUtils.hasLength(referenceJson)) {
            return Collections.emptyList();
        }
        try {
            var root = JSON.parseObject(referenceJson);
            if (root == null) {
                return Collections.emptyList();
            }
            Object content = root.get("content");
            if (content == null) {
                content = root.get("data");
            }
            if (content instanceof JSONArray array) {
                return parseSearchResultArray(array);
            }
            if (content instanceof String contentStr) {
                var array = JSON.parseArray(contentStr);
                if (array != null) {
                    return parseSearchResultArray(array);
                }
            }
            var directArray = JSON.parseArray(referenceJson);
            if (directArray != null) {
                return parseSearchResultArray(directArray);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Collections.emptyList();
    }

    private List<SearchResultDTO> parseSearchResultArray(JSONArray array) {
        var results = new ArrayList<SearchResultDTO>();
        for (int i = 0; i < array.size(); i++) {
            var item = array.getJSONObject(i);
            if (item == null) {
                continue;
            }
            results.add(SearchResultDTO.builder()
                    .url(item.getString("url"))
                    .title(item.getString("title"))
                    .content(item.getString("content"))
                    .build());
        }
        return results;
    }

    private List<String> parseRecommend(String recommendJson) {
        if (!StringUtils.hasLength(recommendJson)) {
            return Collections.emptyList();
        }
        try {
            var root = JSON.parseObject(recommendJson);
            if (root != null) {
                Object content = root.get("content");
                if (content instanceof JSONArray array) {
                    return array.toJavaList(String.class);
                }
                if (content instanceof String contentStr) {
                    var array = JSON.parseArray(contentStr);
                    if (array != null) {
                        return array.toJavaList(String.class);
                    }
                }
            }
            var directArray = JSON.parseArray(recommendJson);
            if (directArray != null) {
                return directArray.toJavaList(String.class);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Collections.emptyList();
    }

    public void update(Long id,
                       StringBuilder finalAnswerBuffer,
                       StringBuilder thinkingBuffer,
                       AgentState agentState,
                       long totalResponseTime,
                       long fistResponseTime,
                       String usedToolsString,
                       String currentRecommendations,
                       String agentType,
                       String referenceJson) {
        if (agentState != null && !agentState.searchResults.isEmpty()) {
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
