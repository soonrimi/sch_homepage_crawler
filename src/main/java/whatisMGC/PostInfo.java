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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
/**/
public class PostInfo {
    private final HtmlFetcher htmlFetcher = new HtmlFetcher();
    private final BoardInfo boardInfo = new BoardInfo();

    // ======================================================================
    // 1. 모든 게시판의 글을 가져오는 메인 크롤링 메서드
    // ======================================================================
    public List<BoardPost> getAllPosts(List<BoardPage> pageList) throws IOException, URISyntaxException {
        List<BoardPost> allPosts = new ArrayList<>();

        System.out.println("모든 게시판의 글을 탐색합니다.");
        for (BoardPage page : pageList) {
            try {
                System.out.println(">>> " + page.getTitle()  +"의 "+ page.getBoardName()+" 게시판의 모든 글을 탐색합니다..." +page.getAbsoluteUrl());
                Document doc = htmlFetcher.getHTMLDocument(page.getAbsoluteUrl());
                Document boardDoc=null;
                String lastseqStr = LastPostSeqInPostlist(doc);
                int lastseq = 0;
                try {
                    lastseq = Integer.parseInt(lastseqStr);
                } catch (NumberFormatException e) {
                    System.err.println("경고: 마지막 게시물 번호를 파싱할 수 없습니다. 첫 페이지만 처리합니다: " + page.getTitle());
                    // 파싱 실패 시 lastseq는 0이므로, 아래 루프는 i=0일 때만 실행됩니다.
                }

                String boardPageUrlForm = URLFormFromPagenation(doc);

                if (boardPageUrlForm.isEmpty()) {
                    System.err.println("경고: " + page.getTitle() + "에서 페이징 정보를 찾을 수 없어 첫 페이지만 처리합니다.");
                    List<BoardPost> postsOnPage = scrapePostDetailFromBoard(doc, pageList);
                    allPosts.addAll(postsOnPage);
                    continue;
                }

                for (int i = 0; i <= lastseq / 10; i++) {
                    String boardurl;
                    if (i == 0) {
                        boardurl = doc.location();
                    } else {
                        boardurl = boardPageUrlForm + (i * 10);
                    }

                    if (boardurl.isEmpty() || !boardurl.startsWith("http")) {
                        System.err.println("경고: 유효하지 않은 URL 생성. 크롤링을 중단합니다: " + boardurl);
                        break;
                    }

                    Document boardListDoc = htmlFetcher.getHTMLDocument(boardurl);
                    List<BoardPost> postsOnPage = scrapePostDetailFromBoard(boardListDoc, pageList);
                    allPosts.addAll(postsOnPage);

                    if (postsOnPage.isEmpty()) {
                        System.out.println("해당 게시판의 모든 페이지를 탐색했습니다.");
                        break;
                    }
                }
            }catch (Exception e) {
                System.out.println(e.getMessage());
            }

        }
        return allPosts;
    }

    // ======================================================================
    // 2. 오늘 올라온 게시물만 가져오는 필터링 메서드
    // ======================================================================
    public List<BoardPost> getDailyPosts(List<BoardPage> pageList) throws IOException, URISyntaxException {
        List<BoardPost> dailyPosts = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        String todayString = today.format(formatter);
        System.out.println(todayString + "일에 올라온 글을 확인합니다.");


        for (BoardPage page : pageList) {
            System.out.println(">>> " + page.getTitle()  +"의 "+ page.getBoardName()+" 게시판의 모든 글을 탐색합니다..." +page.getAbsoluteUrl());
            try{
                Document doc = htmlFetcher.getHTMLDocument(page.getAbsoluteUrl());
                boolean stopCrawling = false;
                String boardPageUrlForm=URLFormFromPagenation(doc);
                boolean foundTodayPost = false;
                for (int i = 0; ; i++) {
                    String boardurl;
                    if (i == 0) {
                        boardurl = doc.location(); // 첫 페이지는 원본 URL
                    } else {
                        boardurl = boardPageUrlForm + ((i - 1) * 10);
                    }

                    if (boardurl.isEmpty() || !boardurl.startsWith("http")) {
                        System.err.println("경고: 유효하지 않은 URL 생성. 크롤링을 중단합니다: " + page.getAbsoluteUrl());
                        break;
                    }

                    Document boardListDoc = htmlFetcher.getHTMLDocument(boardurl);
                    List<BoardPost> postsOnPage = scrapePostDetailFromBoard(boardListDoc, pageList);

                    for (BoardPost post : postsOnPage) {
                        try {
                            LocalDate postDate = LocalDate.parse(post.getpostDate());

                            if (postDate.isEqual(today)) {
                                dailyPosts.add(post);
                            } else if (postDate.isBefore(today)) {
                                stopCrawling = true;
                                break;
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    }

                    if (stopCrawling) {
                        break;
                    }

                }
            } catch (Exception err) {
                System.err.printf("오류 발생: URL '%s' 처리 중 문제 발생. 건너뜁니다. (%s)\n",page.getTitle() , err.getMessage());
            }

        }
        return dailyPosts;
    }

    // ======================================================================
    // 3. 게시물 상세 정보를 스크래핑하는 헬퍼 메서드
    // ======================================================================
    public List<BoardPost> scrapePostDetailFromBoard(Document doc, List<BoardPage> pages) {
        List<BoardPost> postsOnPage = new ArrayList<>();
        Elements posts = doc.select(".type_board > tbody > tr");
        String combinedSelector = "td.subject a, td> a[href*='article_no='],li a[href*='board_no='], .listTable tr td a";
        for (Element post : posts) {
            Element linkElement = post.selectFirst(combinedSelector);

            if (linkElement == null) {
                continue; // 링크가 없는 행은 건너뜁니다.
            }
            String postlink = linkElement.attr("abs:href");

            try {
                Document postDoc = htmlFetcher.getHTMLDocument(postlink);
                if (postDoc == null) {
                    continue; // 상세 페이지를 가져오지 못하면 건너뜁니다.
                }

                // --- 게시물의 공통 정보를 안전하게 가져옵니다. ---
                String posttitle = postDoc.selectFirst(".board_title > :is(h1, h2, h3, h4, h5, h6)").text();

                // 💡 작성자: Null 및 형식 오류에 대비한 안정적인 추출 로직 (메소드 내 직접 구현)
                String postauthor = "";
                Element authorElement = postDoc.selectFirst("ul > li:contains(작성자)");
                if (authorElement != null) {
                    String[] parts = authorElement.text().split(":", 2);
                    if (parts.length > 1) {
                        postauthor = parts[1].trim();
                    }
                }

                // 💡 등록일: 안정적인 추출 로직
                String postdate = "";
                Element dateElement = postDoc.selectFirst("ul > li:contains(등록일)");
                if (dateElement != null) {
                    String[] parts = dateElement.text().split(":", 2);
                    if (parts.length > 1) {
                        postdate = parts[1].trim();
                    }
                }

                // 💡 조회수: 안정적인 추출 로직
                String hits = "";
                Element hitsElement = postDoc.selectFirst("ul > li:contains(조회수)");
                if (hitsElement != null) {
                    String[] parts = hitsElement.text().split(":", 2);
                    if (parts.length > 1) {
                        hits = parts[1].trim();
                    }
                }

                String content = postDoc.select(".board_contents > div.sch_link_target").text();

                String postdepartment = null;
                if (postlink != null && !postlink.isEmpty()) {
                    try {
                        String baseUrl = htmlFetcher.extractBaseURLFromURL(postlink);
                        for (BoardPage page : pages) {
                            if (page.getAbsoluteUrl().contains(baseUrl)) {
                                postdepartment = page.getTitle();
                                break;
                            }
                        }
                    } catch (URISyntaxException e) {
                        System.err.println("오류: department를 찾는 중 URL 구문 분석 실패: " + postlink);
                    }
                }

                if (postdepartment == null) {
                    System.err.println("경고: 게시물 URL에 해당하는 department를 찾을 수 없습니다: " + postlink);
                    continue;
                }

                // --- 첨부파일 처리 ---
                Elements fileLinks = postDoc.select("a[href*='attach_no=']");
                if (fileLinks.isEmpty()) {
                    postsOnPage.add(new BoardPost(postdepartment, posttitle, postauthor, postdate, hits, postlink, content, null));
                } else {
                    for (Element link : fileLinks) {
                        String attachmentUrl = link.attr("abs:href");
                        postsOnPage.add(new BoardPost(postdepartment, posttitle, postauthor, postdate, hits, postlink, content, attachmentUrl));
                    }
                }

            } catch (Exception e) {
                System.err.println("오류: 게시물 상세 페이지 처리 중 예외가 발생했습니다: " + e.getMessage() + " (URL: " + postlink + ")");
            }
        }
        return postsOnPage;
    }

    // ======================================================================
    // 4. 페이징 URL 폼을 생성하는 헬퍼 메서드
    // ======================================================================
    public String URLFormFromPagenation(Document doc) throws URISyntaxException {

        String combinedSelector=("a.pager, a.pager.next, a.pager.last, a[title*='마지막으로'], a[title*='다음으로'],a[title*='다음'], a[title*='맨뒤'], a[title*='pagego']");
        Element pagerElement = doc.selectFirst(combinedSelector);

        if (pagerElement == null) {
            System.err.println("경고: 페이저 엘리먼트를 찾을 수 없습니다. 페이징 URL 생성을 건너뜁니다.");
            return "";
        }

        String nextURL = pagerElement.attr("abs:href");
        if (nextURL.isEmpty()) {
            System.err.println("경고: 페이저 링크의 href 속성이 비어있습니다.");
            return "";
        }

        URI uri = new URI(nextURL);
        String modeValue = null;
        String boardNoValue = null;
        String URLExceptquery = uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
        String query = uri.getQuery();

        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length > 1) {
                    if ("mode".equals(keyValue[0])) {
                        modeValue = keyValue[1];
                    } else if ("board_no".equals(keyValue[0])) {
                        boardNoValue = keyValue[1];
                    }
                }
            }
        }

        // URL 쿼리 파라미터가 없으면 빈 문자열로 설정
        if (modeValue == null) modeValue = "";
        if (boardNoValue == null) boardNoValue = "";

        return URLExceptquery + "?mode=" + modeValue + "&board_no=" + boardNoValue + "&pager.offset=";
    }

    // ======================================================================
    // 5. 게시판 페이지 계산을 위한 헬퍼 메서드
    // ======================================================================
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
/*

public class PostInfo {
    private final HtmlFetcher htmlFetcher = new HtmlFetcher();
    // BoardInfo 인스턴스는 필요에 따라 주입받는 것이 좋습니다.
    private final BoardInfo boardInfo = new BoardInfo();

    // ----------------------------------------------------------------------
    // 오늘 올라온 게시물만 가져오기
    // ----------------------------------------------------------------------
    public List<BoardPost> getDailyPosts(List<BoardPage> pageList) throws IOException, URISyntaxException {
        List<BoardPost> dailyPosts = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        String todayString = today.format(formatter);
        System.out.println(todayString + "일에 올라온 글을 확인합니다.");

        for (BoardPage page : pageList) {
            System.out.println(">>> " + page.getTitle() + " 게시판의 새 글을 탐색합니다..." + page.getAbsoluteUrl());
            Document doc = htmlFetcher.getHTMLDocument(page.getAbsoluteUrl());

            String boardPageUrlForm = URLFormFromPagenation(doc);

            if (boardPageUrlForm.isEmpty()) {
                System.err.println("경고: " + page.getTitle() + "에서 페이징 정보를 찾을 수 없어 첫 페이지만 처리합니다.");
                List<BoardPost> postsOnPage = scrapePostDetailFromBoard(doc, page);
                postsOnPage.stream()
                        .filter(post -> post.getpostDate() != null && post.getpostDate().startsWith(todayString))
                        .forEach(dailyPosts::add);
                continue;
            }

            boolean stopCrawling = false;
            for (int i = 0; !stopCrawling; i++) {
                String boardurl;
                if (i == 0) {
                    boardurl = doc.location();
                } else {
                    boardurl = boardPageUrlForm + ((i - 1) * 10);
                }

                if (boardurl.isEmpty() || !boardurl.startsWith("http")) {
                    System.err.println("경고: 유효하지 않은 URL 생성. 크롤링을 중단합니다: " + boardurl);
                    break;
                }

                Document boardListDoc = htmlFetcher.getHTMLDocument(boardurl);
                List<BoardPost> postsOnPage = scrapePostDetailFromBoard(boardListDoc, page);

                for (BoardPost post : postsOnPage) {
                    try {
                        LocalDate postDate = LocalDate.parse(post.getpostDate());

                        if (postDate.isEqual(today)) {
                            dailyPosts.add(post);
                        } else if (postDate.isBefore(today)) {
                            stopCrawling = true;
                            break;
                        }
                    } catch (Exception e) {
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

    // ----------------------------------------------------------------------
    // 모든 게시판의 글 가져오기
    // ----------------------------------------------------------------------
    public List<BoardPost> getAllPosts(List<BoardPage> pageList) throws IOException, URISyntaxException {
        List<BoardPost> allPosts = new ArrayList<>();
        System.out.println("모든 게시판의 글을 탐색합니다.");

        for (BoardPage page : pageList) {
            System.out.println(">>> " + page.getTitle() + " 게시판의 모든 글을 탐색합니다..." + page.getAbsoluteUrl());
            Document doc = htmlFetcher.getHTMLDocument(page.getAbsoluteUrl());

            String lastseqStr = LastPostSeqInPostlist(doc);
            int lastseq = 0;
            try {
                lastseq = Integer.parseInt(lastseqStr);
            } catch (NumberFormatException e) {
                System.err.println("경고: 마지막 게시물 번호를 파싱할 수 없습니다. 첫 페이지만 처리합니다: " + page.getTitle());
            }

            String boardPageUrlForm = URLFormFromPagenation(doc);

            if (boardPageUrlForm.isEmpty()) {
                System.err.println("경고: " + page.getTitle() + "에서 페이징 정보를 찾을 수 없어 첫 페이지만 처리합니다.");
                List<BoardPost> postsOnPage = scrapePostDetailFromBoard(doc, page);
                allPosts.addAll(postsOnPage);
                continue;
            }

            for (int i = 0; i <= lastseq / 10; i++) {
                String boardurl;
                if (i == 0) {
                    boardurl = doc.location();
                } else {
                    boardurl = boardPageUrlForm + (i * 10);
                }

                if (boardurl.isEmpty() || !boardurl.startsWith("http")) {
                    System.err.println("경고: 유효하지 않은 URL 생성. 크롤링을 중단합니다: " + boardurl);
                    break;
                }

                Document boardListDoc = htmlFetcher.getHTMLDocument(boardurl);
                List<BoardPost> postsOnPage = scrapePostDetailFromBoard(boardListDoc, page);
                allPosts.addAll(postsOnPage);

                if (postsOnPage.isEmpty()) {
                    System.out.println("해당 게시판의 모든 페이지를 탐색했습니다.");
                    break;
                }
            }
        }
        return allPosts;
    }

    // ----------------------------------------------------------------------
    // 게시물 정보 가져오기
    // ----------------------------------------------------------------------
    public List<BoardPost> scrapePostDetailFromBoard(Document doc, BoardPage page) throws IOException, URISyntaxException {
        List<BoardPost> postsOnPage = new ArrayList<>();
        Elements posts = doc.select(".type_board > tbody:nth-child(4) > tr");

        for (Element post : posts) {
            String postlink = post.select("td.subject").select("a").attr("abs:href");

            if (postlink.isEmpty()) {
                System.err.println("경고: 게시물 링크가 비어있습니다. 해당 게시물은 건너뜁니다.");
                continue;
            }

            try {
                Document Postdoc = htmlFetcher.getHTMLDocument(postlink);
                String posttitle = post.select(".subject").text();
                String authorText = post.select(".writer").text().trim();
                String postauthor = authorText.split(" ").length > 1 ? authorText.split(" ")[1] : authorText.split(" ")[0];
                String dateText = post.select(".date").text().trim();
                String postdate = dateText.split(" ").length > 1 ? dateText.split(" ")[1] : dateText.split(" ")[0];
                String hits = post.select(".hits").text().trim();
                String content = Postdoc.select(".board_contents>div.sch_link_target").text();

                String postdepartment = page.getTitle(); // 현재 page 객체에서 department 정보 직접 가져옴

                if (postdepartment == null) {
                    System.err.println("경고: 게시물 URL에 해당하는 department를 찾을 수 없습니다: " + postlink);
                    continue;
                }

                boolean isDuplicate = false;
                for(BoardPost existingPost : postsOnPage) {
                    if(existingPost.getTitle().equals(posttitle) && existingPost.getAuthor().equals(postauthor)) {
                        isDuplicate = true;
                        System.out.println("중복 게시물 발견, 추가하지 않음: " + posttitle);
                        break;
                    }
                }
                if (!isDuplicate) {
                    postsOnPage.add(new BoardPost(postdepartment, posttitle, postauthor, postdate, hits, postlink, content));
                }
            } catch (RuntimeException e) {
                System.err.println("오류: 게시물 상세 페이지를 로드하는 중 예외가 발생했습니다: " + e.getMessage() + " (URL: " + postlink + ")");
            }
        }
        return postsOnPage;
    }

    // ----------------------------------------------------------------------
    // 다음페이지 버튼을 통해서 해당 페이지에서 다음 페이지로 이동할 수 있도록 url 획득
    // ----------------------------------------------------------------------
    public String URLFormFromPagenation(Document doc) throws URISyntaxException {
        Element pagerElement = doc.selectFirst("a[class*=\"pager\"]");

        if (pagerElement == null) {
            System.err.println("경고: 페이저 엘리먼트를 찾을 수 없습니다. 페이징 URL 생성을 건너뜁니다.");
            return "";
        }
        String nextURL = pagerElement.attr("abs:href");
        if (nextURL.isEmpty()) {
            System.err.println("경고: 페이저 링크의 href 속성이 비어있습니다.");
            return "";
        }
        URI uri = new URI(nextURL);
        String modeValue = null;
        String boardNoValue = null;
        String urlExceptQuery = uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
        String query = uri.getQuery();

        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length > 1) {
                    if ("mode".equals(keyValue[0])) {
                        modeValue = keyValue[1];
                    } else if ("board_no".equals(keyValue[0])) {
                        boardNoValue = keyValue[1];
                    }
                }
            }
        }
        if (modeValue == null) modeValue = "";
        if (boardNoValue == null) boardNoValue = "";
        return urlExceptQuery + "?mode=" + modeValue + "&board_no=" + boardNoValue + "&pager.offset=";
    }

    // ----------------------------------------------------------------------
    // 페이지 내 가장 마지막 게시물 번호 가져오기 -> 게시판 페이지 계산
    // ----------------------------------------------------------------------
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
*/