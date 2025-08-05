package whatisMGC;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PostInfo {
    private final HtmlFetcher htmlFetcher = new HtmlFetcher();
    private final BoardInfo boardInfo = new BoardInfo();
    public List<BoardPost> getDailyPosts(List<BoardPage> pageList) throws IOException, URISyntaxException {
        List<BoardPost> dailyPosts = new ArrayList<>();
        LocalDate today = LocalDate.now();
        System.out.println( today +"일에 올라온 글을 확인합니다.");

        for (BoardPage page : pageList) {
            System.out.println(">>> " + page.getTitle() + " 게시판의 새 글을 탐색합니다...");
            Document doc = htmlFetcher.getHTMLDocument(page.getAbsoluteUrl());
            Document boardDoc=null;
            switch (page.getCategorize()){
                case "대학공지" :
                    boardDoc = doc;
                    break;
                case "학과":
                    boardDoc = boardInfo.connectAnnounceLinkURL(doc);
                    break;

                case "센터" :

                    boardDoc = htmlFetcher.getHTMLDocument(doc.select("#gnb > nav:nth-child(2) > ul:nth-child(2) > li:nth-child(4) > a:nth-child(1)").attr("abs:href"));
                    break;
                default:
                    boardDoc = doc;
                    break;
            }

            boolean stopCrawling = false;
            String boardPageUrlForm=URLFormFromPagenation(boardDoc);
            for (int i = 0; !stopCrawling; i++) {
                String boardurl = boardPageUrlForm + (i * 10);
                Document boardListDoc = htmlFetcher.getHTMLDocument(boardurl);
                List<BoardPost> postsOnPage = scrapePostDetailFromBoard(boardListDoc, pageList);

                for (BoardPost post : postsOnPage) {
                    try {
                        LocalDate postDate = LocalDate.parse(post.getPostTime());

                        if (postDate.isEqual(today)) {
                            dailyPosts.add(post);
                        } else if (postDate.isBefore(today)) {
                            stopCrawling = true;
                            break;
                        }
                    } catch (Exception e) {
                        System.err.println("날짜 파싱 오류: " + post.getPostTime() + " - 게시글 건너뛰기");
                        continue;
                    }
                }

                if (stopCrawling) {
                    break;
                }

            }
        }
        return dailyPosts;
    }

    public List<BoardPost> getAllPosts(List<BoardPage> pageList) throws IOException, URISyntaxException {
        List<BoardPost> allPosts = new ArrayList<>();

        System.out.println("모든 게시판의 글을 탐색합니다.");
        for (BoardPage page : pageList) {
            System.out.println(">>> " + page.getTitle() + " 게시판의 새 글을 탐색합니다...");
            Document doc = htmlFetcher.getHTMLDocument(page.getAbsoluteUrl());
            Document boardDoc=null;
            switch (page.getCategorize()){
                case "대학공지" :
                    boardDoc = doc;
                    break;
                case "학과":
                    boardDoc = boardInfo.connectAnnounceLinkURL(doc);
                    break;

                case "센터" :

                    boardDoc = htmlFetcher.getHTMLDocument(doc.select("#gnb > nav:nth-child(2) > ul:nth-child(2) > li:nth-child(4) > a:nth-child(1)").attr("abs:href"));
                    break;
            }
            String lastseqStr = LastPostSeqInPostlist(boardDoc);

            int lastseq = 0;
            lastseq =Integer.parseInt(lastseqStr);

            String boardPageUrlForm=URLFormFromPagenation(boardDoc);


            for (int i=0;i<=lastseq/10;i++) {
                String boardurl = boardPageUrlForm + (i * 10);
                Document boardListDoc = htmlFetcher.getHTMLDocument(boardurl);
                List<BoardPost> postsOnPage = scrapePostDetailFromBoard(boardListDoc, pageList);
                allPosts.addAll(postsOnPage);
            }
        }
        return allPosts;
    }
    public List<BoardPost> scrapePostDetailFromBoard(Document doc, List<BoardPage> pages) throws IOException, URISyntaxException {
        List<BoardPost> postsOnPage = new ArrayList<>();
        Elements posts = doc.select(".type_board > tbody:nth-child(4) > tr");
        for(Element post : posts){
                String postdepartment=null;
                String posttitle = post.select(".subject").text();
                String postauthor = post.select(".writer").text().trim().split(" ")[1];
                String postdate = post.select(".date").text().trim().split(" ")[1];
                String postlink = post.select(".subject>a").attr("abs:href");
                Document Postdoc = htmlFetcher.getHTMLDocument(postlink);
                String content = Postdoc.select(".board_contents>div.sch_link_target").text();
            for (BoardPage page : pages) {
                if (page.getAbsoluteUrl().contains(htmlFetcher.extractBaseURLFromURL(postlink))) {
                    postdepartment = page.getTitle();
                }
            }

            if (postdepartment == null) {
                System.err.println("경고: 게시물 URL에 해당하는 department를 찾을 수 없습니다: " + postlink);
                continue;
            }

            postsOnPage.add(new BoardPost(postdepartment, posttitle, postauthor, postdate, postlink, content));
        }

        return postsOnPage;
    }
    public  String URLFormFromPagenation(Document doc) throws MalformedURLException, URISyntaxException {
        String nextURL = doc.selectFirst("a.pager").attr("abs:href");
        URI uri = new URI(nextURL);
        String modeValue = null;
        String boardNoValue = null;
        String URLExceptquery = uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
        String URLquery = uri.getQuery();
        if (URLquery != null && !URLquery.isEmpty()) {
            String[] pairs = URLquery.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = pair.substring(0, idx);
                    String value = pair.substring(idx + 1);

                    if (key.equals("mode")) {
                        modeValue = value;
                    } else if (key.equals("board_no")) {
                        boardNoValue = value;
                    }
                }
            }
        }
        return URLExceptquery + "?mode=" + modeValue + "&board_no=" + boardNoValue + "&pager.offset=";
    }
    public String LastPostSeqInPostlist(Document doc) {
        Elements seqElement = doc.select(".type_board > tbody:nth-child(4) tr:last-child > td.seq");
        if (!seqElement.isEmpty()) {
            String seqText = seqElement.text().trim();
            if (seqText.matches("\\d+")) {
                return seqText;
            }
        }
        System.err.println("경고: 마지막 게시물 번호를 찾을 수 없거나 유효하지 않습니다.");
        return "0";
    }
    }




