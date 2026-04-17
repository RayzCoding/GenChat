package com.genchat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genchat.converter.AiPptInstConverter;
import com.genchat.dto.AiPptInst;
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
    public AiPptInst saveInst(AiPptInst dto) {
        AiPptInstEntity entity = AiPptInstConverter.INSTANCE.toEntity(dto);
        save(entity);
        return AiPptInstConverter.INSTANCE.toDto(entity);
    }

    /**
     * Query PPT instance by id
     */
    public Optional<AiPptInst> getInstById(Long id) {
        return Optional.ofNullable(getById(id))
                .map(AiPptInstConverter.INSTANCE::toDto);
    }

    /**
     * Query PPT instances by conversation id
     */
    public List<AiPptInst> listByConversationId(String conversationId) {
        var wrapper = new LambdaQueryWrapper<AiPptInstEntity>()
                .eq(AiPptInstEntity::getConversationId, conversationId)
                .orderByDesc(AiPptInstEntity::getCreateTime);
        return AiPptInstConverter.INSTANCE.toDtoList(list(wrapper));
    }

    /**
     * Update PPT instance
     */
    public void updateInst(AiPptInst dto) {
        AiPptInstEntity entity = AiPptInstConverter.INSTANCE.toEntity(dto);
        updateById(entity);
    }

    /**
     * Delete PPT instance by id
     */
    public void deleteInstById(Long id) {
        removeById(id);
    }
}
