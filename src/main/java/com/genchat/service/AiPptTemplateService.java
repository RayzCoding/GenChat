package com.genchat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
    public AiPptTemplateEntity saveTemplate(AiPptTemplateEntity entity) {
        save(entity);
        return entity;
    }

    /**
     * Query template by id
     */
    public Optional<AiPptTemplateEntity> getTemplateById(Long id) {
        return Optional.ofNullable(getById(id));
    }

    /**
     * Query template by template code
     */
    public Optional<AiPptTemplateEntity> getByTemplateCode(String templateCode) {
        var wrapper = new LambdaQueryWrapper<AiPptTemplateEntity>()
                .eq(AiPptTemplateEntity::getTemplateCode, templateCode);
        return Optional.ofNullable(getOne(wrapper));
    }

    /**
     * Get all templates
     */
    public List<AiPptTemplateEntity> listAll() {
        return list();
    }

    /**
     * Update template
     */
    public void updateTemplate(AiPptTemplateEntity entity) {
        updateById(entity);
    }

    /**
     * Delete template by id
     */
    public void deleteTemplateById(Long id) {
        removeById(id);
    }
}
