DROP TABLE IF EXISTS tb_blog_comments;
DROP TABLE IF EXISTS tb_blog;
DROP TABLE IF EXISTS tb_user;
CREATE TABLE tb_user (
    id BIGINT PRIMARY KEY, phone VARCHAR(32), password VARCHAR(128),
    nick_name VARCHAR(64), icon VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE tb_blog (
    id BIGINT PRIMARY KEY, shop_id BIGINT, user_id BIGINT, title VARCHAR(255),
    images VARCHAR(2048), content VARCHAR(2048), liked INT DEFAULT 0, comments INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE tb_blog_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, blog_id BIGINT NOT NULL,
    -- H2 按 UTF-16 长度计数；业务层仍限制为255个 Unicode 字符，与 MySQL utf8mb4 一致。
    parent_id BIGINT NOT NULL, answer_id BIGINT NOT NULL, content VARCHAR(510) NOT NULL,
    liked INT DEFAULT 0, status INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_blog_parent_time ON tb_blog_comments (blog_id, parent_id, create_time, id);
INSERT INTO tb_user (id, nick_name, icon) VALUES (1, '作者甲', '/one.png'), (2, '作者乙', '/two.png');
INSERT INTO tb_blog (id, user_id, comments) VALUES (1, 1, 0), (2, 2, 0);
