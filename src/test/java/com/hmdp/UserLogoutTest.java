package com.hmdp;

import com.hmdp.config.MvcConfig;
import com.hmdp.controller.BlogCommentsController;
import com.hmdp.controller.UserController;
import com.hmdp.dto.Result;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@ContextConfiguration(classes = UserLogoutTest.Config.class)
@WebAppConfiguration
class UserLogoutTest {

    @Configuration
    @EnableWebMvc
    @Import({MvcConfig.class, UserController.class, UserServiceImpl.class, BlogCommentsController.class})
    static class Config {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        IUserInfoService userInfoService() {
            return mock(IUserInfoService.class);
        }

        @Bean
        UserMapper userMapper() {
            return mock(UserMapper.class);
        }

        @Bean
        IBlogCommentsService blogCommentsService() {
            return mock(IBlogCommentsService.class);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private IBlogCommentsService comments;

    @Mock
    private HashOperations<String, Object, Object> hashes;

    private MockMvc mvc;
    private final Map<String, Map<Object, Object>> sessions = new HashMap<>();

    @BeforeEach
    void setup() {
        reset(redis, comments);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        lenient().when(redis.opsForHash()).thenReturn(hashes);
        lenient().when(hashes.entries(anyString())).thenAnswer(invocation ->
                sessions.getOrDefault(invocation.getArgument(0), Collections.emptyMap()));
        lenient().when(redis.delete(anyString())).thenAnswer(invocation ->
                sessions.remove(invocation.getArgument(0)) != null);
    }

    @AfterEach
    void cleanup() {
        UserHolder.removeUser();
    }

    @Test
    void logoutInvalidatesOnlyCurrentSessionAndClearsThreadUser() throws Exception {
        addSession("current");
        addSession("other-device");
        mvc.perform(get("/user/me").header("authorization", "current"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(1));
        mvc.perform(post("/user/logout").header("authorization", "current"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertNull(UserHolder.getUser());
        assertFalse(sessions.containsKey(LOGIN_USER_KEY + "current"));
        assertTrue(sessions.containsKey(LOGIN_USER_KEY + "other-device"));
        mvc.perform(get("/user/me").header("authorization", "current"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/user/me").header("authorization", "other-device"))
                .andExpect(status().isOk());
    }

    @Test
    void repeatedAndExpiredLogoutAreIdempotent() throws Exception {
        addSession("current");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/user/logout").header("authorization", "current"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        }
        mvc.perform(post("/user/logout").header("authorization", "expired"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void missingAndBlankTokenDoNotDeleteRedisPrefix() throws Exception {
        mvc.perform(post("/user/logout"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(post("/user/logout").header("authorization", "  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(redis, never()).delete(anyString());
    }

    @Test
    void commentsRequireLoginForEveryEndpoint() throws Exception {
        mvc.perform(post("/blog-comments").contentType("application/json")
                        .content("{\"blogId\":1,\"content\":\"评论\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/blog-comments/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/blog-comments/of/blog/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/blog-comments/of/comment/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(comments);
    }

    @Test
    void authenticatedCommentRequestsBindParametersAndIgnoreForgedIdentity() throws Exception {
        addSession("current");
        when(comments.saveComment(any())).thenReturn(Result.ok(42L));
        when(comments.queryComments(1L, 1)).thenReturn(Result.ok(Collections.emptyList(), 0L));
        when(comments.queryReplies(42L, 2)).thenReturn(Result.ok(Collections.emptyList(), 0L));
        when(comments.deleteComment(42L)).thenReturn(Result.ok());
        mvc.perform(post("/blog-comments").header("authorization", "current")
                        .contentType("application/json")
                        .content("{\"blogId\":1,\"content\":\"评论\",\"userId\":999,\"status\":2,\"liked\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(42));
        verify(comments).saveComment(argThat(form -> form.getBlogId() == 1L && "评论".equals(form.getContent())));
        mvc.perform(get("/blog-comments/of/blog/1").header("authorization", "current"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/blog-comments/of/comment/42").param("current", "2").header("authorization", "current"))
                .andExpect(status().isOk());
        mvc.perform(delete("/blog-comments/42").header("authorization", "current"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(comments).queryComments(1L, 1);
        verify(comments).queryReplies(42L, 2);
        verify(comments).deleteComment(42L);
        assertNull(UserHolder.getUser());
    }

    private void addSession(String token) {
        Map<Object, Object> user = new HashMap<>();
        user.put("id", "1");
        user.put("nickName", "测试用户");
        sessions.put(LOGIN_USER_KEY + token, user);
    }
}
