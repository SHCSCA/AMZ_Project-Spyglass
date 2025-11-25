package com.amz.spyglass.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * V2.1 F-BIZ-001: 关键词排名历史记录实体。
 * 记录在特定日期，某个ASIN在特定关键词下的搜索排名。
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "keyword_rank_history", indexes = {
        @Index(name = "idx_keyword_rank_history_date_asin", columnList = "scrapeDate, asinKeyword_id")
})
public class KeywordRankHistory extends BaseEntityModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asin_keyword_id", nullable = false)
    private AsinKeywords asinKeyword;

    @Column(nullable = false)
    private LocalDate scrapeDate;

    /**
     * 自然排名。如果未找到，则为-1。
     */
    @Column(nullable = false)
    private Integer naturalRank = -1;

    /**
     * 广告排名。如果未找到，则为-1。
     */
    @Column(nullable = false)
    private Integer sponsoredRank = -1;

    /**
     * 排名所在的页数。如果未找到，则为-1。
     */
    @Column(nullable = false)
    private Integer page = -1;

    public KeywordRankHistory(AsinKeywords asinKeyword, LocalDate scrapeDate, Integer naturalRank, Integer sponsoredRank, Integer page) {
        this.asinKeyword = asinKeyword;
        this.scrapeDate = scrapeDate;
        if (naturalRank != null) this.naturalRank = naturalRank;
        if (sponsoredRank != null) this.sponsoredRank = sponsoredRank;
        if (page != null) this.page = page;
    }
}
