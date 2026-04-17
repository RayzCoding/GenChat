package com.genchat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genchat.entity.AiPptInstEntity;
import com.genchat.repository.AiPptInstRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AiPptInstService extends ServiceImpl<AiPptInstRepository, AiPptInstEntity> {

    /**
     * Save a PPT instance
     */
    public AiPptInstEntity saveInst(AiPptInstEntity entity) {
        save(entity);
        return entity;
    }

    /**
     * Query PPT instance by id
     */
    public Optional<AiPptInstEntity> getInstById(Long id) {
        return Optional.ofNullable(getById(id));
    }

    /**
     * Query PPT instances by conversation id
     */
    public List<AiPptInstEntity> listByConversationId(String conversationId) {
        var wrapper = new LambdaQueryWrapper<AiPptInstEntity>()
                .eq(AiPptInstEntity::getConversationId, conversationId)
                .orderByDesc(AiPptInstEntity::getCreateTime);
        return list(wrapper);
    }

    /**
     * Update PPT instance
     */
    public void updateInst(AiPptInstEntity entity) {
        updateById(entity);
    }

    /**
     * Delete PPT instance by id
     */
    public void deleteInstById(Long id) {
        removeById(id);
    }
}
