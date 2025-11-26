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
     * @return Optional<Integer> 排名。如果未在前N页找到，则返回empty。
     */
    public Optional<Integer> fetchKeywordRank(String keyword, String targetAsin, String site) {
        String baseUrl = "https://www.amazon.com"; // 可根据site动态切换
        String nextUrlPath;

        try {
            nextUrlPath = "/s?k=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            logger.error("Failed to encode keyword: {}", keyword, e);
            return Optional.empty();
        }

        int position = 0;
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
            Elements results = doc.select("div[data-component-type='s-search-result'][data-asin]");
            if (results.isEmpty()) {
                // 回退选择器，兼容旧版页面
                results = doc.select("div.s-result-item[data-asin]:not([data-asin=''])");
            }

            for (Element result : results) {
                String asin = result.attr("data-asin");
                if (asin != null && !asin.isEmpty()) {
                    position++;
                    if (targetAsin.equals(asin)) {
                        logger.info("Found ASIN {} at rank {}.", targetAsin, position);
                        return Optional.of(position);
                    }
                }
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

        logger.warn("ASIN {} not found within the first {} pages for keyword '{}'.", targetAsin, MAX_PAGES_TO_CHECK, keyword);
        return Optional.empty();
    }
}

