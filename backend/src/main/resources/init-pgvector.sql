-- 在 Hibernate 建表之前创建 pgvector 扩展，否则 vector 类型不存在会导致 knowledge_chunks 建表失败
CREATE EXTENSION IF NOT EXISTS vector;
