package com.amz.spyglass.scraper;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KeywordRankScraper {

    private static final Logger logger = LoggerFactory.getLogger(KeywordRankScraper.class);

    // JsoupScraper 应该由Spring注入，这里假设它存在并提供一个 getDocument 方法
    private final JsoupScraper jsoupScraper;

    public KeywordRankScraper(JsoupScraper jsoupScraper) {
        this.jsoupScraper = jsoupScraper;
    }

    // “下一页”按钮的选择器
    private static final String NEXT_PAGE_SELECTOR = "a.s-pagination-item.s-pagination-next";
    private static final int MAX_PAGES_TO_CHECK = 5; // 为避免性能问题和被封锁，最多检查前5页

    /**
     * 抓取指定ASIN在特定关键词下的自然搜索排名。
     *
     * @param keyword 要搜索的关键词
     * @param targetAsin 目标ASIN
     * @param site 亚马逊站点（如 "US"）
     * @return KeywordRankResult 排名结果
     */
    public KeywordRankResult fetchKeywordRank(String keyword, String targetAsin, String site) {
        String baseUrl = "https://www.amazon.com"; // 可根据site动态切换
        String nextUrlPath;

        try {
            nextUrlPath = "/s?k=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            logger.error("Failed to encode keyword: {}", keyword, e);
            return KeywordRankResult.notFound();
        }

        int naturalRankCounter = 0;
        int sponsoredRankCounter = 0;
        int foundNaturalRank = -1;
        int foundSponsoredRank = -1;
        int foundPage = -1;

        for (int currentPage = 1; currentPage <= MAX_PAGES_TO_CHECK; currentPage++) {
            String currentUrl = baseUrl + nextUrlPath;
            logger.info("Scraping keyword rank for ASIN {} on page {}. URL: {}", targetAsin, currentPage, currentUrl);

            Optional<Document> docOpt = jsoupScraper.getDocument(currentUrl);
            if (docOpt.isEmpty()) {
                logger.warn("Failed to fetch document for keyword ranking page {}", currentPage);
                break; // 如果无法获取页面，则终止后续分页
            }
            Document doc = docOpt.get();

            // 使用与 ScrapeParser 一致的增强选择器逻辑
            // 同时选择自然搜索结果 (s-search-result) 和 广告结果 (sp-sponsored-result)
            Elements results = doc.select("div[data-component-type='s-search-result'][data-asin], div[data-component-type='sp-sponsored-result'][data-asin]");
            
            if (results.isEmpty()) {
                // 回退选择器，兼容旧版页面
                results = doc.select("div.s-result-item[data-asin]:not([data-asin=''])");
            }

            logger.info("Found {} items on page {}", results.size(), currentPage);
            
            // DEBUG: Check for Sponsored text presence
            Elements sponsoredTexts = doc.select(":containsOwn(Sponsored)");
            logger.info("DEBUG: Found {} elements containing 'Sponsored' text on page {}", sponsoredTexts.size(), currentPage);

            for (Element result : results) {
                String asin = result.attr("data-asin");
                if (asin == null || asin.isEmpty()) {
                    continue;
                }

                // DEBUG: Log first few items to inspect structure
                if (currentPage == 1 && results.indexOf(result) < 5) {
                     logger.info("DEBUG Item {}: ASIN={}, class='{}', data-component-type='{}', text='{}'", 
                        results.indexOf(result), asin, result.className(), result.attr("data-component-type"), 
                        result.text().substring(0, Math.min(result.text().length(), 50)));
                }

                boolean isSponsored = isSponsored(result);
                if (isSponsored) {
                    sponsoredRankCounter++;
                } else {
                    naturalRankCounter++;
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("Page {} {} Rank {}: Found ASIN {}", currentPage, isSponsored ? "Ad" : "Natural", isSponsored ? sponsoredRankCounter : naturalRankCounter, asin);
                }

                if (targetAsin.equals(asin)) {
                    if (isSponsored) {
                        if (foundSponsoredRank == -1) {
                            foundSponsoredRank = sponsoredRankCounter;
                            logger.info("Found ASIN {} as SPONSORED rank {} on page {}.", targetAsin, foundSponsoredRank, currentPage);
                            if (foundPage == -1) foundPage = currentPage;
                        }
                    } else {
                        if (foundNaturalRank == -1) {
                            foundNaturalRank = naturalRankCounter;
                            logger.info("Found ASIN {} as NATURAL rank {} on page {}.", targetAsin, foundNaturalRank, currentPage);
                            if (foundPage == -1) foundPage = currentPage;
                        }
                    }
                }
            }

            // 如果自然排名和广告排名都找到了，可以提前结束
            // 或者根据需求，也许我们只想找自然排名？但用户要求两者。
            // 这里我们策略是：只要找到其中一个，我们继续找另一个，直到页面结束或者都找到。
            // 但为了效率，如果都找到了，就退出。
            if (foundNaturalRank != -1 && foundSponsoredRank != -1) {
                break;
            }

            // 查找下一页的链接
            Element nextPageLink = doc.selectFirst(NEXT_PAGE_SELECTOR);
            if (nextPageLink != null) {
                nextUrlPath = nextPageLink.attr("href");
            } else {
                logger.info("No 'next page' link found. Reached the end of search results for keyword '{}'.", keyword);
                break; // 没有下一页了
            }
        }

        if (foundNaturalRank != -1 || foundSponsoredRank != -1) {
            return new KeywordRankResult(foundNaturalRank, foundSponsoredRank, foundPage);
        }

        logger.warn("ASIN {} not found within the first {} pages for keyword '{}'.", targetAsin, MAX_PAGES_TO_CHECK, keyword);
        return KeywordRankResult.notFound();
    }

    /**
     * 判断搜索结果项是否为广告（Sponsored）。
     */
    private boolean isSponsored(Element element) {
        // 策略0: 检查 data-component-type
        String componentType = element.attr("data-component-type");
        if ("sp-sponsored-result".equals(componentType)) {
            return true;
        }

        // 策略1: 检查是否有 "Sponsored" 文本标签
        // 常见的类名: .puis-sponsored-label-text, .s-label-popover-default
        if (!element.select(".puis-sponsored-label-text, .s-sponsored-label-text").isEmpty()) {
            return true;
        }
        
        // 策略2: 检查是否有 AdHolder 类 (有时在父级，但有时也在自身或子级)
        if (element.hasClass("AdHolder") || !element.select(".AdHolder").isEmpty()) {
            return true;
        }

        // 策略3: 暴力检查文本内容 (作为最后的兜底)
        // 注意：这可能会误判，如果标题里包含 "Sponsored" (极少见)
        // 但通常 Sponsored 标签是可见文本的一部分
        String text = element.text();
        if (text.contains("Sponsored") || text.contains("Ad")) {
             // 进一步验证，避免误判标题
             // 检查是否在特定的 label 区域
             if (!element.select("span:containsOwn(Sponsored)").isEmpty()) {
                 return true;
             }
        }

        return false;
    }
}

