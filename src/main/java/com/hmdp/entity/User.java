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
@TableName("tb_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 涓婚敭
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 鎵嬫満鍙风爜
     */
    private String phone;

    /**
     * 瀵嗙爜锛屽姞瀵嗗瓨鍌?
     */
    private String password;

    /**
     * 鏄电О锛岄粯璁ゆ槸闅忔満瀛楃
     */
    private String nickName;

    /**
     * 鐢ㄦ埛澶村儚
     */
    private String icon = "";

    /**
     * 鍒涘缓鏃堕棿
     */
    private LocalDateTime createTime;

    /**
     * 鏇存柊鏃堕棿
     */
    private LocalDateTime updateTime;


}
