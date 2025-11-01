package com.amz.spyglass.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Asin 实体类：表示需要监控的亚马逊商品
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-29
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "asin")
public class AsinModel extends BaseEntityModel {

    /**
     * 主键ID，自增长
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Amazon 标准识别号，不可为空
     */
    @Column(nullable = false)
    private String asin;

    /**
     * 站点代码，如 US、UK，不可为空
     */
    @Column(nullable = false)
    private String site;

    /**
     * 自定义昵称，用于在UI中显示
     */
    private String nickname;

    /**
     * 库存预警阈值，低于此值触发告警
     */
    private Integer inventoryThreshold;

    /**
     * 获取用于显示的名称，优先使用昵称，否则返回ASIN
     * @return 显示名称
     */
    @Transient
    public String getNicknameOrAsin() {
        return (nickname != null && !nickname.trim().isEmpty()) ? nickname : asin;
    }
}
