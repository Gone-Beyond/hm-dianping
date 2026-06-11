package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog_comments")
public class BlogComments implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 涓婚敭
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 鐢ㄦ埛id
     */
    private Long userId;

    /**
     * 鎺㈠簵id
     */
    private Long blogId;

    /**
     * 鍏宠仈鐨?绾ц瘎璁篿d锛屽鏋滄槸涓€绾ц瘎璁猴紝鍒欏€间负0
     */
    private Long parentId;

    /**
     * 鍥炲鐨勮瘎璁篿d
     */
    private Long answerId;

    /**
     * 鍥炲鐨勫唴瀹?
     */
    private String content;

    /**
     * 鐐硅禐鏁?
     */
    private Integer liked;

    /**
     * 鐘舵€侊紝0锛氭甯革紝1锛氳涓炬姤锛?锛氱姝㈡煡鐪?
     */
    private Boolean status;

    /**
     * 鍒涘缓鏃堕棿
     */
    private LocalDateTime createTime;

    /**
     * 鏇存柊鏃堕棿
     */
    private LocalDateTime updateTime;


}
