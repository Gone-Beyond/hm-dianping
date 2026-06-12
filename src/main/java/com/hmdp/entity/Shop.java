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
@TableName("tb_shop")
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 涓婚敭
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 鍟嗛摵鍚嶇О
     */
    private String name;

    /**
     * 鍟嗛摵绫诲瀷鐨刬d
     */
    private Long typeId;

    /**
     * 鍟嗛摵鍥剧墖锛屽涓浘鐗囦互','闅斿紑
     */
    private String images;

    /**
     * 鍟嗗湀锛屼緥濡傞檰瀹跺槾
     */
    private String area;

    /**
     * 鍦板潃
     */
    private String address;

    /**
     * 缁忓害
     */
    private Double x;

    /**
     * 缁村害
     */
    private Double y;

    /**
     * 鍧囦环锛屽彇鏁存暟
     */
    private Long avgPrice;

    /**
     * 閿€閲?
     */
    private Integer sold;

    /**
     * 璇勮鏁伴噺
     */
    private Integer comments;

    /**
     * 璇勫垎锛?~5鍒嗭紝涔?0淇濆瓨锛岄伩鍏嶅皬鏁?
     */
    private Integer score;

    /**
     * 钀ヤ笟鏃堕棿锛屼緥濡?10:00-22:00
     */
    private String openHours;

    /**
     * 鍒涘缓鏃堕棿
     */
    private LocalDateTime createTime;

    /**
     * 鏇存柊鏃堕棿
     */
    private LocalDateTime updateTime;


    @TableField(exist = false)
    private Double distance;
}
