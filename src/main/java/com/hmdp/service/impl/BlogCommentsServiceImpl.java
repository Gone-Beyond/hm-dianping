package com.hmdp.service.impl;

import com.hmdp.entity.BlogComments;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.BlogCommentFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hmdp.utils.SystemConstants.DEFAULT_PAGE_SIZE;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    private final BlogMapper blogMapper;
    private final UserMapper userMapper;

    public BlogCommentsServiceImpl(BlogMapper blogMapper, UserMapper userMapper) {
        this.blogMapper = blogMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public Result saveComment(BlogCommentFormDTO form) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (form == null || !validId(form.getBlogId())) {
            return Result.fail("笔记id不合法");
        }
        if (StrUtil.isBlank(form.getContent())) {
            return Result.fail("评论内容不能为空");
        }
        String content = form.getContent().trim();
        if (content.codePointCount(0, content.length()) > 255) {
            return Result.fail("评论内容不能超过255个字符");
        }
        long parentId = form.getParentId() == null ? 0 : form.getParentId();
        long answerId = form.getAnswerId() == null ? 0 : form.getAnswerId();
        if (parentId < 0 || answerId < 0 || (parentId == 0 && answerId != 0)) {
            return Result.fail("评论回复关系不合法");
        }

        // 同一笔记的新增、回复、删除先锁定笔记，避免回复与删除并发造成数量不一致。
        if (blogMapper.selectIdForUpdate(form.getBlogId()) == null) {
            return Result.fail("笔记不存在");
        }
        if (parentId > 0) {
            BlogComments parent = getById(parentId);
            if (!visible(parent) || !form.getBlogId().equals(parent.getBlogId())
                    || !Long.valueOf(0).equals(parent.getParentId())) {
                return Result.fail("一级评论不存在或不属于当前笔记");
            }
            if (answerId == 0) {
                answerId = parentId;
            }
            BlogComments answer = answerId == parentId ? parent : getById(answerId);
            if (!visible(answer) || !form.getBlogId().equals(answer.getBlogId())
                    || (answerId != parentId && !Long.valueOf(parentId).equals(answer.getParentId()))) {
                return Result.fail("回复的评论不存在或不属于当前评论");
            }
        }

        BlogComments comment = new BlogComments()
                .setBlogId(form.getBlogId())
                .setUserId(user.getId())
                .setParentId(parentId)
                .setAnswerId(answerId)
                .setContent(content)
                .setLiked(0)
                .setStatus(0);
        if (!save(comment)) {
            throw new IllegalStateException("保存评论失败");
        }
        updateCommentCount(form.getBlogId(), 1);
        return Result.ok(comment.getId());
    }

    @Override
    public Result queryComments(Long blogId, Integer current) {
        if (!validId(blogId) || current == null || current < 1) {
            return Result.fail("笔记id或页码不合法");
        }
        if (blogMapper.selectById(blogId) == null) {
            return Result.fail("笔记不存在");
        }
        return queryPage(new QueryWrapper<BlogComments>()
                .eq("blog_id", blogId).eq("parent_id", 0).lt("status", 2)
                .orderByDesc("create_time", "id"), current);
    }

    @Override
    public Result queryReplies(Long parentId, Integer current) {
        if (!validId(parentId) || current == null || current < 1) {
            return Result.fail("评论id或页码不合法");
        }
        BlogComments parent = getById(parentId);
        if (!visible(parent) || !Long.valueOf(0).equals(parent.getParentId())) {
            return Result.fail("一级评论不存在");
        }
        return queryPage(new QueryWrapper<BlogComments>()
                .eq("blog_id", parent.getBlogId()).eq("parent_id", parentId).lt("status", 2)
                .orderByAsc("create_time", "id"), current);
    }

    @Override
    @Transactional
    public Result deleteComment(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (!validId(id)) {
            return Result.fail("评论id不合法");
        }
        BlogComments comment = getById(id);
        if (comment == null || blogMapper.selectIdForUpdate(comment.getBlogId()) == null) {
            return Result.fail("评论或笔记不存在");
        }
        // 使用锁定读重新获取状态，避免事务快照读到其他删除操作提交前的数据。
        comment = baseMapper.selectOne(new QueryWrapper<BlogComments>().eq("id", id).last("FOR UPDATE"));
        if (!user.getId().equals(comment.getUserId())) {
            return Result.fail("只能删除自己的评论");
        }
        if (!visible(comment)) {
            return Result.ok();
        }

        UpdateWrapper<BlogComments> wrapper = new UpdateWrapper<BlogComments>()
                .eq("blog_id", comment.getBlogId()).lt("status", 2);
        if (Long.valueOf(0).equals(comment.getParentId())) {
            wrapper.and(condition -> condition.eq("id", id).or().eq("parent_id", id));
        } else {
            wrapper.eq("id", id);
        }
        // 软删除保留回复引用；删除一级评论时同时隐藏它的全部回复。
        int removed = baseMapper.update(null, wrapper.set("status", 2));
        if (removed == 0) {
            throw new IllegalStateException("删除评论失败");
        }
        updateCommentCount(comment.getBlogId(), -removed);
        return Result.ok();
    }

    private Result queryPage(QueryWrapper<BlogComments> wrapper, Integer current) {
        Page<BlogComments> page = page(new Page<>(current, DEFAULT_PAGE_SIZE), wrapper);
        List<BlogComments> comments = page.getRecords();
        if (!comments.isEmpty()) {
            Set<Long> userIds = comments.stream().map(BlogComments::getUserId).collect(Collectors.toSet());
            Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            for (BlogComments comment : comments) {
                User author = users.get(comment.getUserId());
                if (author != null) {
                    comment.setName(author.getNickName()).setIcon(author.getIcon());
                }
            }
        }
        return Result.ok(comments, page.getTotal());
    }

    private void updateCommentCount(Long blogId, int delta) {
        if (blogMapper.updateCommentCount(blogId, delta) != 1) {
            throw new IllegalStateException("更新笔记评论数量失败");
        }
    }

    private boolean validId(Long id) {
        return id != null && id > 0;
    }

    private boolean visible(BlogComments comment) {
        return comment != null && comment.getStatus() != null && comment.getStatus() < 2;
    }

}
