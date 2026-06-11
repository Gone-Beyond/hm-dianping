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
@TableName("tb_voucher_order")
public class VoucherOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 涓婚敭
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 涓嬪崟鐨勭敤鎴穒d
     */
    private Long userId;

    /**
     * 璐拱鐨勪唬閲戝埜id
     */
    private Long voucherId;

    /**
     * 鏀粯鏂瑰紡 1锛氫綑棰濇敮浠橈紱2锛氭敮浠樺疂锛?锛氬井淇?
     */
    private Integer payType;

    /**
     * 璁㈠崟鐘舵€侊紝1锛氭湭鏀粯锛?锛氬凡鏀粯锛?锛氬凡鏍搁攢锛?锛氬凡鍙栨秷锛?锛氶€€娆句腑锛?锛氬凡閫€娆?
     */
    private Integer status;

    /**
     * 涓嬪崟鏃堕棿
     */
    private LocalDateTime createTime;

    /**
     * 鏀粯鏃堕棿
     */
    private LocalDateTime payTime;

    /**
     * 鏍搁攢鏃堕棿
     */
    private LocalDateTime useTime;

    /**
     * 閫€娆炬椂闂?
     */
    private LocalDateTime refundTime;

    /**
     * 鏇存柊鏃堕棿
     */
    private LocalDateTime updateTime;


}
