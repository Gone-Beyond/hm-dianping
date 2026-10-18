package com.hmdp.service;

import com.hmdp.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.BlogCommentFormDTO;
import com.hmdp.dto.Result;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    Result saveComment(BlogCommentFormDTO form);

    Result queryComments(Long blogId, Integer current);

    Result queryReplies(Long parentId, Integer current);

    Result deleteComment(Long id);

}
