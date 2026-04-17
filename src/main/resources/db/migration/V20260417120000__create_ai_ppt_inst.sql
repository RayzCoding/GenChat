CREATE TABLE IF NOT EXISTS `ai_ppt_inst` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Instance ID',
    `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Conversation ID',
    `template_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Selected template code',
    `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'INIT' COMMENT 'Status',
    `query` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'User original query',
    `requirement` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Requirement clarification',
    `search_info` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Search information',
    `outline` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Outline',
    `ppt_schema` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'AI generated PPT schema JSON',
    `file_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Generated PPT file URL',
    `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Error message',
    `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_conversation_id`(`conversation_id` ASC) USING BTREE,
    INDEX `idx_status`(`status` ASC) USING BTREE,
    INDEX `idx_template_code`(`template_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI PPT generation instance table' ROW_FORMAT = Dynamic;
