# 黑马点评

基于 Spring Boot、MyBatis-Plus、MySQL 和 Redis 的后端学习项目，包含验证码登录、商铺缓存、优惠券秒杀、探店笔记、关注动态、附近商铺、签到与笔记评论。

## 启动

1. 准备 MySQL 与 Redis，Redis 需要支持消息流和地理位置查询命令。
2. 新建 `hmdp` 数据库，首次初始化时导入 `src/main/resources/db/hmdp.sql`。该脚本含删表语句，请勿在已有业务数据的数据库中直接重新导入。
3. 将 `src/main/resources/application.yaml.template` 复制为同目录的 `application.yaml`，填写本地数据库和 Redis 配置。实际配置已被 Git 忽略。
4. 执行 `mvn spring-boot:run`，默认端口为 `8081`。图片上传路径见 `SystemConstants.IMAGE_UPLOAD_DIR`，使用上传功能前需按本机目录调整。

已有数据库如需添加评论查询索引，执行一次 `src/main/resources/db/migration/V2__blog_comments_index.sql`。全新导入初始化脚本后无需重复执行。本项目未集成自动数据库迁移，此脚本需要手动执行。

## 退出登录与笔记评论

请求头 `authorization` 直接传登录接口返回的凭证，与项目现有登录机制保持一致。评论接口均需要登录；退出登录允许凭证缺失或失效，重复调用仍返回成功。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/user/logout` | 删除当前凭证对应的 Redis 登录信息，并清理当前线程的用户信息；其他设备的凭证继续有效 |
| POST | `/blog-comments` | 发表一级评论或回复，返回新评论编号 |
| GET | `/blog-comments/of/blog/{blogId}?current=1` | 一级评论分页，按创建时间与编号倒序，每页5条 |
| GET | `/blog-comments/of/comment/{parentId}?current=1` | 指定一级评论下的回复分页，按创建时间与编号正序，每页5条 |
| DELETE | `/blog-comments/{id}` | 删除本人评论；删除一级评论时同时隐藏其全部回复 |

发表评论的请求体：

```json
{
  "blogId": 4,
  "content": "这家店不错"
}
```

回复评论时，增加 `parentId`（所属一级评论编号）；可增加 `answerId`（具体回复的评论编号），省略时回复一级评论。两者必须属于同一笔记、同一评论串。

评论内容不能为空，最多255个 Unicode 字符。用户编号、评论状态和初始点赞数量由服务端设置。分页结果沿用 `Result`，`data` 为记录列表，`total` 为对应一级评论或回复的总数，列表包含作者昵称 `name` 和头像 `icon`。

评论采用软删除，隐藏记录不出现在列表中。删除单条回复会保留其原有回复引用；删除一级评论会隐藏整个评论串。笔记的 `comments` 统计一级评论和回复之和；新增、删除与数量更新在同一事务中执行，同一笔记的评论写操作通过数据库行锁串行处理。

## 验证说明

新增验证代码位于 `BlogCommentsServiceTest` 和 `UserLogoutTest`，分别覆盖评论数据库事务与登录拦截器链路；评论验证使用独立 H2 内存数据库。既有 `HmDianPingApplicationTests` 含本地数据库与 Redis 数据写入实验，执行前请确认连接环境。
