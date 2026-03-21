-- 在 Hibernate 建表之前创建 pgvector 扩展，否则 vector 类型不存在会导致 knowledge_chunks 建表失败
-- （与 application.yml 中 spring.sql.init.separator: ;; 配合：每条“逻辑语句”以 ;; 结束）
CREATE EXTENSION IF NOT EXISTS vector;;

-- 题库 type 列曾由 Hibernate 生成 CHECK；Java 枚举新增 ALGORITHM 后 ddl-auto 不会自动放宽旧约束，会导致插入 ALGORITHM 失败。
-- 若表已存在则删除过时约束，由 JPA 枚举继续约束取值。
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = current_schema() AND table_name = 'questions'
  ) THEN
    ALTER TABLE questions DROP CONSTRAINT IF EXISTS questions_type_check;
  END IF;
END $$;;
