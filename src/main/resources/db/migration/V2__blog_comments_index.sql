-- 已有数据库执行一次；全新导入 hmdp.sql 已包含此索引，无需重复执行。
ALTER TABLE tb_blog_comments
    ADD INDEX idx_blog_parent_time (blog_id, parent_id, create_time, id);
