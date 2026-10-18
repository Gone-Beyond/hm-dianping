package com.hmdp;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hmdp.config.MybatisConfig;
import com.hmdp.dto.BlogCommentFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.BlogComments;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.impl.BlogCommentsServiceImpl;
import com.hmdp.utils.UserHolder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(BlogCommentsServiceTest.Config.class)
@Sql("/comments-schema.sql")
class BlogCommentsServiceTest {

    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.hmdp.mapper")
    @Import({BlogCommentsServiceImpl.class, MybatisConfig.class})
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:comments;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource, MybatisPlusInterceptor interceptor) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPlugins(interceptor);
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    @Autowired
    private IBlogCommentsService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void login() {
        loginAs(1L);
    }

    @AfterEach
    void cleanup() {
        UserHolder.removeUser();
    }

    @Test
    void createsCommentWithServerControlledFieldsAndUpdatesNullableCount() {
        jdbc.update("UPDATE tb_blog SET comments = NULL WHERE id = 1");
        Long id = create(1L, null, null, "  很好吃  ");
        BlogComments stored = service.getById(id);
        assertEquals(1L, stored.getUserId());
        assertEquals(0L, stored.getParentId());
        assertEquals(0L, stored.getAnswerId());
        assertEquals("很好吃", stored.getContent());
        assertEquals(0, stored.getStatus());
        assertEquals(0, stored.getLiked());
        assertEquals(1, count());
    }

    @Test
    void validatesContentAndIdsBeforeWriting() {
        assertFalse(save(0L, null, null, "内容").getSuccess());
        assertFalse(save(1L, null, null, "  ").getSuccess());
        assertFalse(save(1L, null, null, repeat("字", 256)).getSuccess());
        assertFalse(save(99L, null, null, "内容").getSuccess());
        assertFalse(save(1L, -1L, null, "内容").getSuccess());
        assertFalse(save(1L, 0L, 1L, "内容").getSuccess());
        assertEquals(0, count());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_blog_comments", Integer.class));
    }

    @Test
    void supportsEmojiAtTheDatabaseCharacterLimit() {
        create(1L, null, null, repeat("🍜", 255));
        assertFalse(save(1L, null, null, repeat("🍜", 256)).getSuccess());
        assertEquals(1, count());
    }

    @Test
    void rejectsWritesWithoutLogin() {
        Long id = create(1L, null, null, "一级评论");
        UserHolder.removeUser();
        assertFalse(save(1L, null, null, "内容").getSuccess());
        assertFalse(service.deleteComment(id).getSuccess());
        assertEquals(1, count());
    }

    @Test
    void createsRepliesAndRejectsCrossBlogOrCrossThreadReferences() {
        Long root = create(1L, null, null, "一级评论");
        Long reply = create(1L, root, null, "直接回复");
        Long nested = create(1L, root, reply, "回复楼中楼");
        assertEquals(root, service.getById(reply).getAnswerId());
        assertEquals(root, service.getById(nested).getParentId());
        assertEquals(reply, service.getById(nested).getAnswerId());
        Long otherRoot = create(1L, null, null, "另一条评论");
        assertFalse(save(2L, root, null, "跨笔记").getSuccess());
        assertFalse(save(1L, root, otherRoot, "跨评论").getSuccess());
        assertFalse(save(1L, reply, null, "把回复作为一级评论").getSuccess());
        assertFalse(save(1L, root, 999L, "不存在的回复").getSuccess());
        assertEquals(4, count());
    }

    @Test
    void paginatesRootsAndEnrichesAuthorWithoutLeakingReplies() {
        List<Long> roots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            roots.add(create(1L, null, null, "评论" + i));
        }
        create(1L, roots.get(0), null, "不应出现在一级评论列表");
        Result first = service.queryComments(1L, 1);
        List<BlogComments> records = records(first);
        assertEquals(7L, first.getTotal());
        assertEquals(5, records.size());
        assertEquals(roots.get(6), records.get(0).getId());
        assertEquals("作者甲", records.get(0).getName());
        assertEquals("/one.png", records.get(0).getIcon());
        assertEquals(2, records(service.queryComments(1L, 2)).size());
        assertTrue(records(service.queryComments(1L, 3)).isEmpty());
    }

    @Test
    void queriesRepliesInStableOrderAndFiltersHiddenComments() {
        Long root = create(1L, null, null, "一级评论");
        Long first = create(1L, root, null, "第一条回复");
        Long second = create(1L, root, first, "第二条回复");
        List<BlogComments> replies = records(service.queryReplies(root, 1));
        assertEquals(first, replies.get(0).getId());
        assertEquals(second, replies.get(1).getId());
        jdbc.update("UPDATE tb_blog_comments SET status = 2 WHERE id = ?", first);
        assertEquals(1L, service.queryReplies(root, 1).getTotal());
        assertFalse(save(1L, root, first, "回复隐藏评论").getSuccess());
        assertFalse(service.queryReplies(second, 1).getSuccess());
        assertFalse(service.queryReplies(999L, 1).getSuccess());
        assertFalse(service.queryComments(1L, 0).getSuccess());
        assertFalse(service.queryComments(999L, 1).getSuccess());
    }

    @Test
    void rejectsDeletingOtherUsersComment() {
        Long root = create(1L, null, null, "一级评论");
        loginAs(2L);
        assertFalse(service.deleteComment(root).getSuccess());
        assertEquals(0, service.getById(root).getStatus());
        assertEquals(1, count());
    }

    @Test
    void deletesRootAndRepliesOnceAndRejectsNewReplies() {
        Long root = create(1L, null, null, "一级评论");
        create(1L, root, null, "回复甲");
        loginAs(2L);
        create(1L, root, null, "其他人的回复");
        loginAs(1L);
        assertTrue(service.deleteComment(root).getSuccess());
        assertEquals(0, count());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM tb_blog_comments WHERE status = 2", Integer.class));
        assertEquals(0L, service.queryComments(1L, 1).getTotal());
        assertFalse(service.queryReplies(root, 1).getSuccess());
        assertFalse(save(1L, root, null, "已删除评论的回复").getSuccess());
        assertTrue(service.deleteComment(root).getSuccess());
        assertEquals(0, count());
    }

    @Test
    void deletingReplyKeepsItsChildrenAndDoesNotDoubleDecrement() {
        Long root = create(1L, null, null, "一级评论");
        Long reply = create(1L, root, null, "回复甲");
        Long nested = create(1L, root, reply, "回复乙");
        assertTrue(service.deleteComment(reply).getSuccess());
        assertTrue(service.deleteComment(reply).getSuccess());
        assertEquals(2, count());
        assertEquals(0, service.getById(nested).getStatus());
        assertEquals(reply, service.getById(nested).getAnswerId());
        assertEquals(1L, service.queryReplies(root, 1).getTotal());
        assertTrue(service.deleteComment(root).getSuccess());
        assertEquals(0, count());
    }

    @Test
    void rollsBackNewCommentWhenCounterUpdateFails() {
        jdbc.execute("ALTER TABLE tb_blog ADD CONSTRAINT fail_increment CHECK (comments <= 0)");
        assertThrows(RuntimeException.class, () -> save(1L, null, null, "必须回滚"));
        assertEquals(0, count());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_blog_comments", Integer.class));
    }

    @Test
    void rollsBackDeletionWhenCounterUpdateFails() {
        Long root = create(1L, null, null, "一级评论");
        create(1L, root, null, "回复");
        jdbc.execute("ALTER TABLE tb_blog ADD CONSTRAINT fail_decrement CHECK (id <> 1 OR comments >= 2)");
        assertThrows(RuntimeException.class, () -> service.deleteComment(root));
        assertEquals(2, count());
        assertEquals(2L, service.count());
        assertEquals(0, service.getById(root).getStatus());
    }

    @Test
    void concurrentCreatesKeepTheExactCount() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Result>> results = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    loginAs(1L);
                    try {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return save(1L, null, null, "并发评论");
                    } finally {
                        UserHolder.removeUser();
                    }
                }));
            }
            start.countDown();
            for (Future<Result> result : results) {
                assertTrue(result.get(10, TimeUnit.SECONDS).getSuccess());
            }
            assertEquals(8, count());
            assertEquals(8, service.count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRootDeletionAndReplyNeverLeaveVisibleOrphan() throws Exception {
        Long root = create(1L, null, null, "一级评论");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Result> reply = executor.submit(() -> {
                loginAs(2L);
                try {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return save(1L, root, null, "并发回复");
                } finally {
                    UserHolder.removeUser();
                }
            });
            Future<Result> deletion = executor.submit(() -> {
                loginAs(1L);
                try {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return service.deleteComment(root);
                } finally {
                    UserHolder.removeUser();
                }
            });
            start.countDown();
            reply.get(10, TimeUnit.SECONDS);
            assertTrue(deletion.get(10, TimeUnit.SECONDS).getSuccess());
            assertEquals(0, count());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_blog_comments WHERE status < 2", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    private Result save(Long blogId, Long parentId, Long answerId, String content) {
        BlogCommentFormDTO form = new BlogCommentFormDTO();
        form.setBlogId(blogId);
        form.setParentId(parentId);
        form.setAnswerId(answerId);
        form.setContent(content);
        return service.saveComment(form);
    }

    private Long create(Long blogId, Long parentId, Long answerId, String content) {
        Result result = save(blogId, parentId, answerId, content);
        assertTrue(result.getSuccess(), result.getErrorMsg());
        return (Long) result.getData();
    }

    private int count() {
        return jdbc.queryForObject("SELECT comments FROM tb_blog WHERE id = 1", Integer.class);
    }

    @SuppressWarnings("unchecked")
    private List<BlogComments> records(Result result) {
        assertTrue(result.getSuccess(), result.getErrorMsg());
        return (List<BlogComments>) result.getData();
    }

    private static void loginAs(Long id) {
        UserDTO user = new UserDTO();
        user.setId(id);
        UserHolder.saveUser(user);
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
