package com.amz.spyglass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ASIN 创建/更新请求 DTO
 * 用于接收前端请求创建或更新 ASIN 监控项的输入参数
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-29
 */
@Data
@Schema(description = "ASIN 创建/更新请求 DTO")
public class AsinRequest {

    /**
     * Amazon 标准识别号，不可为空
     */
    @NotBlank(message = "ASIN 不能为空")
    @Schema(description = "Amazon 标准识别号", example = "B0TEST1234")
    private String asin;

    /**
     * 站点代码（如 US、UK），不可为空
     */
    @NotBlank(message = "站点代码不能为空")
    @Schema(description = "站点代码", example = "US")
    private String site;

    /**
     * 自定义昵称，可选
     */
    @Schema(description = "自定义昵称", nullable = true)
    private String nickname;

    /**
     * 库存预警阈值，不可为空
     */
    @NotNull(message = "库存预警阈值不能为空")
    @Schema(description = "库存预警阈值", example = "20")
    private Integer inventoryThreshold;

    /**
     * 品牌（可选）
     */
    @Schema(description = "品牌", nullable = true)
    private String brand;

    /**
     * 所属分组ID（可选），前端选择已有分组时填入。
     */
    @Schema(description = "分组ID", nullable = true)
    private Long groupId;
}
