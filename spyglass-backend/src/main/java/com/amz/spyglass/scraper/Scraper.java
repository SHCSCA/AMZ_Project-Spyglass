package com.amz.spyglass.scraper;

/**
 * 抓取器接口（中文注释）
 * 定义可插拔的抓取器契约，例如 JsoupScraper、SeleniumScraper 等实现都应实现此接口。
 */
public interface Scraper {

    /**
     * 抓取给定 URL 的页面标题（示例方法）
     * @param url 目标 URL
     * @return 页面 title，找不到时返回 null
     * @throws Exception 发生网络或解析错误时抛出异常
     */
    String fetchTitle(String url) throws Exception;

    /**
     * 抓取并解析页面，返回一个快照对象，包含 title/price/bsr/inventory/imageMd5/aplusMd5 等字段。
     * 实现可以选择不下载图片字节以避免额外网络依赖（当前 JsoupScraper 使用图片 URL 的 MD5 作为占位）。
     */
    com.amz.spyglass.scraper.AsinSnapshot fetchSnapshot(String url) throws Exception;
}
