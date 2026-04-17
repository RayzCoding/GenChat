package com.genchat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genchat.converter.AiPptTemplateConverter;
import com.genchat.dto.AiPptTemplate;
import com.genchat.entity.AiPptTemplateEntity;
import com.genchat.repository.AiPptTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AiPptTemplateService extends ServiceImpl<AiPptTemplateRepository, AiPptTemplateEntity> {

    /**
     * Save a PPT template
     */
    public AiPptTemplate saveTemplate(AiPptTemplate dto) {
        AiPptTemplateEntity entity = AiPptTemplateConverter.INSTANCE.toEntity(dto);
        save(entity);
        return AiPptTemplateConverter.INSTANCE.toDto(entity);
    }

    /**
     * Query template by id
     */
    public Optional<AiPptTemplate> getTemplateById(Long id) {
        return Optional.ofNullable(getById(id))
                .map(AiPptTemplateConverter.INSTANCE::toDto);
    }

    /**
     * Query template by template code
     */
    public Optional<AiPptTemplate> getByTemplateCode(String templateCode) {
        var wrapper = new LambdaQueryWrapper<AiPptTemplateEntity>()
                .eq(AiPptTemplateEntity::getTemplateCode, templateCode);
        return Optional.ofNullable(getOne(wrapper))
                .map(AiPptTemplateConverter.INSTANCE::toDto);
    }

    /**
     * Get all templates
     */
    public List<AiPptTemplate> listAll() {
        return AiPptTemplateConverter.INSTANCE.toDtoList(list());
    }

    /**
     * Update template
     */
    public void updateTemplate(AiPptTemplate dto) {
        AiPptTemplateEntity entity = AiPptTemplateConverter.INSTANCE.toEntity(dto);
        updateById(entity);
    }

    /**
     * Delete template by id
     */
    public void deleteTemplateById(Long id) {
        removeById(id);
    }
}
