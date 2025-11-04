package com.amz.spyglass.dto;

import java.time.Instant;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ASIN 响应 DTO
 * 用于返回给前端的 ASIN 对象视图，包含基础配置字段和审计时间戳
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-29
 */
@Data
@Schema(description = "ASIN 基础信息响应 DTO")
public class AsinResponse {

    /**
     * ASIN记录ID
     */
    @Schema(description = "ASIN 记录主键 ID")
    private Long id;

    /**
     * Amazon 标准识别号
     */
    @Schema(description = "Amazon 标准识别号")
    private String asin;

    /**
     * 站点代码，如 US、UK
     */
    @Schema(description = "站点代码，例如 US / UK")
    private String site;

    /**
     * 自定义昵称，用于UI展示
     */
    @Schema(description = "自定义昵称", nullable = true)
    private String nickname;

    /**
     * 库存预警阈值
     */
    @Schema(description = "库存预警阈值", nullable = true)
    private Integer inventoryThreshold;

    /** 品牌 */
    @Schema(description = "品牌", nullable = true)
    private String brand;

    /** 分组ID（如果存在） */
    @Schema(description = "分组ID", nullable = true)
    private Long groupId;

    /** 分组名称（便于前端直接展示） */
    @Schema(description = "分组名称", nullable = true)
    private String groupName;

    /**
     * 记录创建时间
     */
    @Schema(description = "创建时间 (UTC ISO-8601)")
    private Instant createdAt;

    /**
     * 记录最后更新时间
     */
    @Schema(description = "更新时间 (UTC ISO-8601)")
    private Instant updatedAt;
}
