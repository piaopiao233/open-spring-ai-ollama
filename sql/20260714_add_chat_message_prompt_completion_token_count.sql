ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS prompt_token_count int DEFAULT NULL COMMENT '输入token数';
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS completion_token_count int DEFAULT NULL COMMENT '输出token数';
