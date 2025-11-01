import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class test_parser {
    public static void main(String[] args) throws Exception {
        Document doc = Jsoup.parse(new java.io.File("/workspaces/AMZ_Project-Spyglass/spyglass-backend/src/test/resources/dump/B0FSZ63V9Z.html"), "UTF-8");
        
        System.out.println("=== BSR 解析测试 ===");
        Elements bsrRows = doc.select("th:containsOwn(Best Sellers Rank), th:contains(Best Sellers Rank)");
        System.out.println("找到 BSR th 元素数量: " + bsrRows.size());
        
        for (Element bsrHeader : bsrRows) {
            System.out.println("BSR Header text: " + bsrHeader.text());
            Element bsrCell = bsrHeader.nextElementSibling();
            if (bsrCell != null) {
                System.out.println("BSR Cell tag: " + bsrCell.tagName());
                Elements bsrItems = bsrCell.select("li, .a-list-item");
                System.out.println("找到 li 元素数量: " + bsrItems.size());
                
                for (int i = 0; i < bsrItems.size(); i++) {
                    Element item = bsrItems.get(i);
                    String itemText = item.text();
                    System.out.println("\n[Item " + i + "] " + itemText);
                    System.out.println("  包含# ? " + itemText.contains("#"));
                    System.out.println("  包含 in ? " + itemText.contains(" in "));
                    System.out.println("  包含() ? " + itemText.contains("("));
                }
            }
        }
        
        System.out.println("\n=== 评论/评分解析测试 ===");
        Element revEl = doc.selectFirst("[data-hook=total-review-count]");
        System.out.println("评论数元素: " + (revEl != null ? revEl.text() : "NULL"));
        
        Element avgEl = doc.selectFirst("[data-hook=rating-out-of-text]");
        System.out.println("评分元素: " + (avgEl != null ? avgEl.text() : "NULL"));
    }
}
