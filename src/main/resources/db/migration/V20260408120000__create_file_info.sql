CREATE TABLE IF NOT EXISTS `file_info` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key id',
    `name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'file name',
    `path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'file path',
    `file_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'file type (e.g. pdf, docx, png)',
    `size` bigint NULL DEFAULT NULL COMMENT 'file size in bytes',
    `extracted_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'extracted plain text content',
    `embed` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'whether the file has been vectorized: 0-no, 1-yes',
    `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT 'file status: pending, processing, success, failed',
    `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_create_time`(`create_time` ASC) USING BTREE COMMENT 'create time index'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'File information table' ROW_FORMAT = DYNAMIC;
