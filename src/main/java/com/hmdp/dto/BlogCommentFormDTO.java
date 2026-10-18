package com.hmdp.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 评论提交参数，用户身份、状态和点赞数量由服务端设置。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlogCommentFormDTO {
    private Long blogId;
    private Long parentId;
    private Long answerId;
    private String content;
}
