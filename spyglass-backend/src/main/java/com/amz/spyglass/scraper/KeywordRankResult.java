package com.amz.spyglass.scraper;

public class KeywordRankResult {
    private final int naturalRank;
    private final int sponsoredRank;
    private final int page;

    public KeywordRankResult(int naturalRank, int sponsoredRank, int page) {
        this.naturalRank = naturalRank;
        this.sponsoredRank = sponsoredRank;
        this.page = page;
    }

    public static KeywordRankResult notFound() {
        return new KeywordRankResult(-1, -1, -1);
    }

    public int getNaturalRank() {
        return naturalRank;
    }

    public int getSponsoredRank() {
        return sponsoredRank;
    }

    public int getPage() {
        return page;
    }

    public boolean isFound() {
        return naturalRank > 0;
    }
}
