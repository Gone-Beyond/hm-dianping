package com.hmdp.controller;


import com.hmdp.dto.BlogCommentFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    private final IBlogCommentsService blogCommentsService;

    public BlogCommentsController(IBlogCommentsService blogCommentsService) {
        this.blogCommentsService = blogCommentsService;
    }

    @PostMapping
    public Result saveComment(@RequestBody BlogCommentFormDTO form) {
        return blogCommentsService.saveComment(form);
    }

    @GetMapping("/of/blog/{blogId}")
    public Result queryComments(@PathVariable Long blogId,
                                @RequestParam(defaultValue = "1") Integer current) {
        return blogCommentsService.queryComments(blogId, current);
    }

    @GetMapping("/of/comment/{parentId}")
    public Result queryReplies(@PathVariable Long parentId,
                               @RequestParam(defaultValue = "1") Integer current) {
        return blogCommentsService.queryReplies(parentId, current);
    }

    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable Long id) {
        return blogCommentsService.deleteComment(id);
    }

}
