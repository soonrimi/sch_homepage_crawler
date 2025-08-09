package whatisMGC;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import whatisMGC.BoardPage;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class BoardInfo {
    HtmlFetcher htmlFetcher = new HtmlFetcher();

    public List<BoardPage> extractBoardInfo(String selector, Document doc) {
        List<BoardPage> boardPages = new ArrayList<>();
        Elements links = doc.select(selector);

        if (links.isEmpty()) {
            System.err.println("경고: 지정된 셀렉터로 링크를 찾을 수 없습니다: " + selector);
            return boardPages;
        }

        for (Element link : links) {
            // 링크 텍스트와 절대 URL을 사용하여 BoardPage 객체 생성
            String title = link.text().trim();
            String absoluteUrl = link.attr("abs:href");
            if (!title.isEmpty() && !absoluteUrl.isEmpty()) {
                boardPages.add(new BoardPage(title, absoluteUrl, null));
            }
        }
        return boardPages;
    }

    public Document connectAnnounceLinkURL(Document doc) throws IOException {
        String combinedSelector = "#gnb a:contains(공지사항), #gnb a:contains(커뮤니티)";
        System.out.println(combinedSelector);
        String linktext = doc.select(combinedSelector).attr("abs:href");
        if (linktext.isEmpty()) {
            System.err.println("오류: 공지사항 링크를 찾을 수 없습니다.");
            throw new IOException("공지사항 링크가 비어 있습니다.");
        }
        return htmlFetcher.getHTMLDocument(linktext);
    }
    // for get all pages in main website
//    public List<BoardPage> getAllPages(Document doc) {
//        List<BoardPage> allPages = new ArrayList<>();
//
//        allPages.addAll(extractBoardInfo(".type_01 ul li > a", doc));
//        allPages.addAll(extractBoardInfo(".type_02 > ul:nth-child(2)>li>a", doc));
//        allPages.addAll(extractBoardInfo(".sub_6 > div:nth-child(2) > div:nth-child(1) > ul:nth-child(2)>li>a", doc));
//
//        return allPages;
//    }
}