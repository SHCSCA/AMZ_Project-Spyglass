package com.amz.spyglass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ASIN 创建/更新请求 DTO
 * 用于接收前端请求创建或更新 ASIN 监控项的输入参数
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-29
 */
@Data
public class AsinRequest {

    /**
     * Amazon 标准识别号，不可为空
     */
    @NotBlank(message = "ASIN 不能为空")
    private String asin;

    /**
     * 站点代码（如 US、UK），不可为空
     */
    @NotBlank(message = "站点代码不能为空")
    private String site;

    /**
     * 自定义昵称，可选
     */
    private String nickname;

    /**
     * 库存预警阈值，不可为空
     */
    @NotNull(message = "库存预警阈值不能为空")
    private Integer inventoryThreshold;

    /**
     * 品牌（可选）
     */
    private String brand;

    /**
     * 所属分组ID（可选），前端选择已有分组时填入。
     */
    private Long groupId;
}
