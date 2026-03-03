-- 为 vector_store 表添加全文检索支持
-- 使用 simple 分词器（适合中文）

-- 检查 tsvector 列是否已存在，避免重复执行
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'vector_store' AND column_name = 'tsvector_content'
    ) THEN
        -- 添加 tsvector 列
        ALTER TABLE vector_store ADD COLUMN tsvector_content tsvector;

        -- 为现有数据生成 tsvector
        UPDATE vector_store SET tsvector_content = to_tsvector('simple', content);

        -- 创建 GIN 索引用于全文检索
        CREATE INDEX idx_vector_store_tsvector ON vector_store USING GIN (tsvector_content);

        -- 创建触发器，当 content 更新时自动更新 tsvector_content
        CREATE OR REPLACE FUNCTION tsvector_update_trigger() RETURNS trigger AS $$
        BEGIN
            NEW.tsvector_content := to_tsvector('simple', NEW.content);
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;

        DROP TRIGGER IF EXISTS tsvector_update ON vector_store;
        CREATE TRIGGER tsvector_update
            BEFORE INSERT OR UPDATE OF content ON vector_store
            FOR EACH ROW EXECUTE FUNCTION tsvector_update_trigger();

        RAISE NOTICE 'Full-text search support added successfully';
    ELSE
        RAISE NOTICE 'tsvector column already exists, skipping';
    END IF;
END $$;
