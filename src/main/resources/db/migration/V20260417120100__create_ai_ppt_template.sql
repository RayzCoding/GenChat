CREATE TABLE IF NOT EXISTS `ai_ppt_template` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Template ID',
    `template_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Unique template code',
    `template_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Template name',
    `template_desc` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Template description',
    `template_schema` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Template structure JSON',
    `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'PPT template file path',
    `style_tags` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Style tags: tech, business, minimal',
    `slide_count` int NULL DEFAULT NULL COMMENT 'Number of slides in template',
    `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `template_code`(`template_code` ASC) USING BTREE,
    INDEX `idx_template_code`(`template_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI PPT template table' ROW_FORMAT = Dynamic;
