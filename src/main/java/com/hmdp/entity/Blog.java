package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("tb_blog")
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 涓婚敭
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * 鍟嗘埛id
     */
    private Long shopId;
    /**
     * 鐢ㄦ埛id
     */
    private Long userId;
    /**
     * 鐢ㄦ埛鍥炬爣
     */
    @TableField(exist = false)
    private String icon;
    /**
     * 鐢ㄦ埛濮撳悕
     */
    @TableField(exist = false)
    private String name;
    /**
     * 鏄惁鐐硅禐杩囦簡
     */
    @TableField(exist = false)
    private Boolean isLike;

    /**
     * 鏍囬
     */
    private String title;

    /**
     * 鎺㈠簵鐨勭収鐗囷紝鏈€澶?寮狅紝澶氬紶浠?,"闅斿紑
     */
    private String images;

    /**
     * 鎺㈠簵鐨勬枃瀛楁弿杩?
     */
    private String content;

    /**
     * 鐐硅禐鏁伴噺
     */
    private Integer liked;

    /**
     * 璇勮鏁伴噺
     */
    private Integer comments;

    /**
     * 鍒涘缓鏃堕棿
     */
    private LocalDateTime createTime;

    /**
     * 鏇存柊鏃堕棿
     */
    private LocalDateTime updateTime;


}
