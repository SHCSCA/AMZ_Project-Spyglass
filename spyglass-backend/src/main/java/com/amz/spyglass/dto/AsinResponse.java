package com.amz.spyglass.dto;

import java.time.Instant;
import lombok.Data;

/**
 * ASIN 响应 DTO
 * 用于返回给前端的 ASIN 对象视图，包含基础配置字段和审计时间戳
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-29
 */
@Data
public class AsinResponse {

    /**
     * ASIN记录ID
     */
    private Long id;

    /**
     * Amazon 标准识别号
     */
    private String asin;

    /**
     * 站点代码，如 US、UK
     */
    private String site;

    /**
     * 自定义昵称，用于UI展示
     */
    private String nickname;

    /**
     * 库存预警阈值
     */
    private Integer inventoryThreshold;

    /** 品牌 */
    private String brand;

    /** 分组ID（如果存在） */
    private Long groupId;

    /** 分组名称（便于前端直接展示） */
    private String groupName;

    /**
     * 记录创建时间
     */
    private Instant createdAt;

    /**
     * 记录最后更新时间
     */
    private Instant updatedAt;
}
