package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
@TableName("tb_shop_type")
public class ShopType implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 涓婚敭
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 绫诲瀷鍚嶇О
     */
    private String name;

    /**
     * 鍥炬爣
     */
    private String icon;

    /**
     * 椤哄簭
     */
    private Integer sort;

    /**
     * 鍒涘缓鏃堕棿
     */
    @JsonIgnore
    private LocalDateTime createTime;

    /**
     * 鏇存柊鏃堕棿
     */
    @JsonIgnore
    private LocalDateTime updateTime;


}
