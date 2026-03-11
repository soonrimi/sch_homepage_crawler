package whatisMGC;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostInfo {
    private final HtmlFetcher htmlFetcher = new HtmlFetcher();
    private final BoardInfo boardInfo = new BoardInfo();
    // --- 상수 정의 (Constants) ---
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy.MM.dd"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yy.MM.dd"),
        DateTimeFormatter.ofPattern("yy-MM-dd")
    };

    /**
     * 문자열 날짜를 LocalDate 객체로 변환 (다양한 포맷 지원)
     * 변환 실패 시 로그를 남기고 null 반환
     */
    private LocalDate parseToLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        dateStr = dateStr.trim();
        
        // 1. 시간 정보(HH:mm)만 있는 경우 (오늘 날짜)
        if (dateStr.matches("\\d{2}:\\d{2}")) {
            return LocalDate.now();
        }

        // 2. 미리 정의된 포맷터 순회 매칭
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
                // 매칭 실패 시 다음 포맷으로 계속 시도
            }
        }

        // 3. 연도 생략 + 시간 형식 (ex: 10.25 14:30)
        if (dateStr.matches("\\d{2}[.-]\\d{2}\\s+\\d{2}:\\d{2}")) {
            try {
                String datePart = dateStr.split("\\s+")[0];
                int year = LocalDate.now().getYear();
                String pattern = datePart.contains(".") ? "yyyy.MM.dd" : "yyyy-MM-dd";
                return LocalDate.parse(year + (datePart.contains(".") ? "." : "-") + datePart, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }

        // 4. 정규식을 이용한 추출 (ex: "작성일 : 2023-10-25" -> "2023-10-25")
        Pattern pattern = Pattern.compile("(\\d{4})[-./](\\d{2})[-./](\\d{2})");
        Matcher matcher = pattern.matcher(dateStr);
        if (matcher.find()) {
            try {
                String extractedDate = matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
                return LocalDate.parse(extractedDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (DateTimeParseException e) {
                System.err.println("정규식 추출 후 날짜 파싱 실패. 추출 문자열: " + dateStr);
                return null;
            }
        }

        System.err.println("지원하지 않는 날짜 포맷입니다: [" + dateStr + "]");
        return null;
    }

    // ======================================================================
    // 1. 모든 게시판의 글을 가져오는 메인 크롤링 메서드
    // ======================================================================
    public List<BoardPost> getAllPosts(List<BoardPage> pageList) throws IOException, URISyntaxException {
        Set<BoardPost> seen = new HashSet<>();
        List<BoardPost> allPosts = new ArrayList<>();
        LocalDate startDate = LocalDate.of(2024, 1, 1);

        System.out.println("2024년 1월 1일 이후 모든 게시판의 글을 탐색합니다.");
        for (BoardPage page : pageList) {
            if ("자유게시판".equals(page.getBoardName()) || "학사공지".equals(page.getBoardName())) {
                continue;
            }
            try {
                System.out.println(
                        ">>> " + page.getTitle() + "의 " + (page.getBoardName() != null ? page.getBoardName() : "(메인)")
                                + " 게시판 탐색 시작... " + page.getAbsoluteUrl());
                String currentUrl = page.getAbsoluteUrl();
                Document currentDoc = htmlFetcher.getHTMLDocument(currentUrl);

                while (true) {
                    List<BoardPost> postsOnPage = scrapePostDetailFromBoard(currentDoc, pageList);

                    if (postsOnPage.isEmpty()) {
                        System.out.println("페이지에 더 이상 게시물이 없어 탐색을 종료합니다.");
                        break;
                    }

                    for (BoardPost post : postsOnPage) {
                        if (post == null || post.getpostDate() == null) {
                            continue;
                        }
                        if (seen.add(post)) {
                            allPosts.add(post);
                        }
                    }
                    BoardPost lastPost = postsOnPage.get(postsOnPage.size() - 1);
                    LocalDate lastPostDate = lastPost.getpostDate().toLocalDateTime().toLocalDate();
                    if (lastPostDate.isBefore(startDate)) {
                        System.out.printf(" - 페이지의 마지막 게시물('%s')이 %s 이전 글이므로 탐색을 중단합니다.%n",
                                lastPost.getTitle(), startDate);
                        break;
                    }

                    // 다음 페이지로 이동하는 로직
                    String combinedSelector = ".paging a[title*='다음'], .page_list a[title*='다음'],.pager a[title*='다음'], a.pager.next:has(span:contains(다음)), a.pager.next, a:has(img[alt*='다음'])";
                    Element nextElement = currentDoc.selectFirst(combinedSelector);
                    if (nextElement == null) {
                        System.out.println("마지막 페이지입니다. 이 게시판의 탐색을 종료합니다.");
                        break;
                    }

                    String nextUrl = nextElement.attr("abs:href");
                    if (nextUrl.isEmpty() || !nextUrl.startsWith("http")) {
                        System.err.println("경고: 유효하지 않은 다음 페이지 URL. 크롤링을 중단합니다: " + nextUrl);
                        break;
                    }

                    try {
                        long delay = 500 + (long) (Math.random() * 500);
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    currentDoc = htmlFetcher.getHTMLDocument(nextUrl);
                }
                try {
                    long delay = 1000 + (long) (Math.random() * 1000);
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                System.err.println("오류: '" + page.getTitle() + "' 게시판 처리 중 예외 발생: " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("총 " + allPosts.size() + "개의 게시물을 수집했습니다.");
        return allPosts;
    }

    // ======================================================================
    // 2. 올라온 게시물만 가져오는 필터링 메서드
    // ======================================================================
    public List<BoardPost> getDailyPosts(List<BoardPage> pageList) throws IOException, URISyntaxException {
        Set<BoardPost> seen = new HashSet<>();
        List<BoardPost> dailyPosts = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        String todayString = today.format(formatter);
        System.out.println(todayString + "일에 올라온 글을 확인합니다.");

        for (BoardPage page : pageList) {
            if ("자유게시판".equals(page.getBoardName()) || "학사공지".equals(page.getBoardName())) {
                continue;
            }
            try {
                System.out.println(">>> [오늘] " + page.getTitle() + "의 "
                        + (page.getBoardName() != null ? page.getBoardName() : "(메인)") + " 게시판의 모든 글을 탐색합니다... "
                        + page.getAbsoluteUrl());
                String currentUrl = page.getAbsoluteUrl();
                Document currentDoc = htmlFetcher.getHTMLDocument(currentUrl);
                boolean stopCrawling = false;

                while (!stopCrawling) {
                    List<BoardPost> postsOnPage = scrapePostDetailFromBoard(currentDoc, pageList);
                    if (postsOnPage.isEmpty()) {
                        System.out.println(" - 페이지에 더 이상 게시물이 없어 탐색을 종료합니다.");
                        break;
                    }
                    boolean foundTodayPostOnThisPage = false;

                    for (BoardPost post : postsOnPage) {
                        Timestamp timestamp = post.getpostDate();
                        if (timestamp == null) {
                            continue;
                        }

                        LocalDate postDate = timestamp.toLocalDateTime().toLocalDate();

                        if (postDate.isEqual(today)) {
                            if (seen.add(post)) {
                                dailyPosts.add(post);
                            }
                            foundTodayPostOnThisPage = true;
                        } else if (postDate.isBefore(today)) {
                            stopCrawling = true;
                            break;
                        }
                    }

                    if (stopCrawling) {
                        break;
                    }
                    if (!foundTodayPostOnThisPage) {
                        System.out.println(" - 이 페이지에 오늘 날짜의 글이 없어 탐색을 중단합니다.");
                        break;
                    }

                    String combinedSelector = ".paging a[title*='다음'], .page_list a[title*='다음'],.pager a[title*='다음']";
                    Element nextElement = currentDoc.selectFirst(combinedSelector);
                    if (nextElement == null) {
                        System.out.println(" - 마지막 페이지입니다. 이 게시판의 탐색을 종료합니다.");
                        break;
                    }

                    String nextUrl = nextElement.attr("abs:href");
                    if (nextUrl.isEmpty() || !nextUrl.startsWith("http")) {
                        System.err.println("경고: 유효하지 않은 다음 페이지 URL. 크롤링을 중단합니다: " + nextUrl);
                        break;
                    }

                    currentDoc = htmlFetcher.getHTMLDocument(nextUrl);
                }
            } catch (Exception err) {
                System.err.printf("오류 발생: URL '%s' 처리 중 문제 발생. 건너뜁니다. (%s)\n", page.getTitle(), err.getMessage());
            }
        }
        return dailyPosts;
    }

    // ======================================================================
    // 3. 게시물 목록의 행(row) 데이터를 순회하며 스크래핑하는 메인 메서드
    // ======================================================================
    public List<BoardPost> scrapePostDetailFromBoard(Document doc, List<BoardPage> pages) {
        List<BoardPost> postsOnPage = new ArrayList<>();
        // 목록 페이지 내 각 게시물(tr, li 등) 추출 
        Elements posts = doc.select(".type_board tbody tr, .listTable tbody tr, table.lmode tbody tr, table.tbl02 tbody tr, table.list2 tbody tr, table.tbl-noti tbody tr, .xnbpl-body-container tbody tr, ul.gallery_list li, div.card2019, li.ex_slide2_li, table[name^=tbody_list] tbody tr");
        String combinedSelector = "td.subject a, td a[href*='article_no='], li a[href*='board_no='], .listTable tr td a, td.title a, td.tit a, .subject a, td.left a, .ratio_box a, td.ta a";

        for (Element post : posts) {
            // 상단 고정공지/공지사항 스킵 (모든 페이지에서 중복 크롤링 방지)
            if (post.hasClass("always") || post.hasClass("notice") || post.hasClass("top_notice")) {
                continue;
            }

            try {
                Element linkElement = post.selectFirst(combinedSelector);
                
                // 1단계: 목록 페이지(List Row)에서 기본 정보 추출 (특수 게시판 Fallback 포함)
                BoardPost partialPost = parseListRowFallbacks(post, linkElement);
                if (partialPost == null) {
                    continue; // 유효한 링크나 제목을 찾지 못함
                }

                // 2단계: 상세 페이지로 진입하여 본문/조회수/첨부파일 스크래핑 (eclass 등은 제외)
                if (partialPost.getAbsoluteUrl() != null && !partialPost.getAbsoluteUrl().isEmpty() && !partialPost.getAbsoluteUrl().equals("https://eclass.sch.ac.kr")) {
                    fetchAndParseDetail(partialPost);
                }

                // 3단계: URL 패턴을 기반으로 카테고리(Category) 및 부서(Department) 매핑
                determineCategoryAndDepartment(partialPost, pages);

                // 부서 매핑 실패 시 다음 게시물로 (알 수 없는 게시판)
                if (partialPost.getDepartment() == null) {
                    System.err.println("경고: 게시물 URL에 해당하는 department를 찾을 수 없습니다: " + partialPost.getAbsoluteUrl());
                    continue;
                }

                System.out.println("Processing: " + partialPost.getTitle());
                System.out.println("PostLink: " + partialPost.getAbsoluteUrl());
                System.out.println("PostDept: " + partialPost.getDepartment());

                // 4단계: 최종 Timestamp 생성 및 BoardPost 객체 완성
                BoardPost finalPost = buildFinalPost(partialPost);
                if (finalPost != null) {
                    postsOnPage.add(finalPost);
                }

            } catch (Exception e) {
                System.err.println("오류: 게시물 상세 페이지 처리 중 예외 발생: " + e.getMessage() + " (Element: " + post + ")");
            }
        }
        return postsOnPage;
    }

    // ----------------------------------------------------------------------
    // 3-1. 목록 페이지 구조 대응 파서 (특수 게시판 Fallback 처리)
    // ----------------------------------------------------------------------
    private BoardPost parseListRowFallbacks(Element post, Element linkElement) {
        String postlink = "";
        String posttitle = "";
        String postauthor = "";
        String postdate = "";
        Integer hitInt = 0;
        
        // 1) 취업 관련 특수 게시판 구조 (공채속보)
        if (post.selectFirst("td a[onclick^=openw]") != null && post.selectFirst("span") != null && post.select("td").size() > 2) {
            Element titleSpan = post.selectFirst("td span");
            Element aTag = post.selectFirst("td a[onclick^=openw]");
            if (titleSpan != null && aTag != null) {
                posttitle = titleSpan.text().trim();
                String onclick = aTag.attr("onclick");
                Matcher m = Pattern.compile("openw\\('(.*?)'").matcher(onclick);
                if (m.find()) {
                    postlink = m.group(1);
                    if (!postlink.startsWith("http")) postlink = "https://id.sch.ac.kr" + (postlink.startsWith("/") ? "" : "/") + postlink;
                }
                Element authorSpan = post.selectFirst("td small");
                postauthor = (authorSpan != null) ? authorSpan.text().trim() : "관리자";
                Elements tds = post.select("td");
                if (tds.size() >= 5) {
                    postdate = tds.get(3).text().trim() + " ~ " + tds.get(4).text().trim();
                }
            }
        } 
        // 2) 취업센터 카드형 구조 (학교제공 채용정보 등)
        else if (post.hasClass("card2019") || !post.select("div.card2019").isEmpty()) {
            Element aTag = post.selectFirst("h4 a") != null ? post.selectFirst("h4 a") : post.selectFirst("h4");
            Element cardBody = post.selectFirst("div.card-body[onclick^=detailView]");
            if (aTag != null && cardBody != null) {
                posttitle = aTag.text().trim();
                Matcher m = Pattern.compile("detailView\\('(.*?)'").matcher(cardBody.attr("onclick"));
                if (m.find()) postlink = "https://id.sch.ac.kr/Recruit/RecruitSchoolView.aspx?rcdx=" + m.group(1);
                postauthor = "관리자";
                postdate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
        } 
        // 3) 취업센터 슬라이드형 (진로프로그램)
        else if (post.hasClass("ex_slide2_li") || !post.select("li.ex_slide2_li").isEmpty()) {
            Element h6Tag = post.selectFirst("h6");
            Element aTag = post.selectFirst("a[onclick^=openw]");
            if (h6Tag != null && aTag != null) {
                posttitle = h6Tag.text().trim();
                Matcher m = Pattern.compile("openw\\('(.*?)'").matcher(aTag.attr("onclick"));
                if (m.find()) {
                    postlink = m.group(1);
                    if (!postlink.startsWith("http")) postlink = "https://id.sch.ac.kr" + (postlink.startsWith("/") ? "" : "/") + postlink;
                }
                postauthor = "관리자";
            }
        } 
        // 4) 표준형 일반 게시판
        else if (linkElement != null) {
            postlink = linkElement.attr("abs:href");
            posttitle = linkElement.text();
            if (linkElement.hasAttr("onclick") && linkElement.attr("onclick").contains("window.open")) {
                Matcher m = Pattern.compile("window\\.open\\('(.*?)'").matcher(linkElement.attr("onclick"));
                if (m.find()) postlink = "https://id.sch.ac.kr" + m.group(1);
            }
        } 
        // 5) eclass 구조
        else if (post.selectFirst(".xnbpl-post-title") != null) {
            posttitle = post.selectFirst(".xnbpl-post-title").text();
            postdate = post.selectFirst(".xnbpl-createdat").text();
            postauthor = "관리자";
            Element viewEl = post.selectFirst(".xnbpl-views");
            if (viewEl != null) hitInt = safelyParseInt(viewEl.text().replaceAll("[^0-9]", "")).orElse(0);
        } else {
            return null; // 링크/제목 없음
        }

        // 일반 목록 페이지 td/히스토릭 맵핑 Fallback
        Elements tds = post.select("td");
        if (!tds.isEmpty()) {
            for (int i = 0; i < tds.size(); i++) {
                String t = tds.get(i).text().trim();
                if (t.matches("\\d{2,4}[-.]\\d{2}[-.]\\d{2}")) {
                    postdate = t;
                    if (i > 0) {
                        String possibleAuthor = tds.get(i - 1).text().trim();
                        if (!possibleAuthor.isEmpty() && possibleAuthor.length() < 15 && !possibleAuthor.matches(".*\\d.*")) {
                            postauthor = possibleAuthor;
                        }
                    }
                }
            }
            String lastTd = tds.last().text().trim().replaceAll("[^0-9]", "");
            if (!lastTd.isEmpty()) hitInt = safelyParseInt(lastTd).orElse(hitInt);
        }

        // 기타 폴백
        if (post.selectFirst(".ratio_box .title") != null) {
            posttitle = post.selectFirst(".ratio_box .title").text();
            if (postauthor.isEmpty()) postauthor = "관리자";
            if (postdate.isEmpty()) postdate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }

        // 임시 DTO 셋팅 (department, timestamp 등은 빈 채로 전달)
        // category 인자에는 null을 넘긴다.
        BoardPost tempPost = new BoardPost(null, posttitle, postauthor, null, hitInt, postlink, posttitle, new ArrayList<>(), new ArrayList<>(), null, "");
        // 내부적 전달을 위해 임시로 date 문자열을 contentHash 필드에 끼워넣기 (후속 파싱 위함)
        tempPost.setContentHash(postdate); 
        return tempPost;
    }

    // ----------------------------------------------------------------------
    // 3-2. 상세 페이지 본문/조회수/첨부파일 다운로드
    // ----------------------------------------------------------------------
    private void fetchAndParseDetail(BoardPost partialPost) {
        String titleSelector = ".board_title > :is(h1, h2, h3, h4, h5, h6), td.title, type_board tbody [class*=\"subject\"], .subject, .vmode th, .board_view_title .tit, .arti_title, .tbl-top .subject, .view_tit .v_tit, .boardInfoTitle";
        String authorSelector = "ul > li:contains(작성자), .writer span, [class*=\"writer\"], td.writer, .writer, .vmode td:contains(작성자) + td, .board_view_info .writer, .tbl-top .info p:contains(작성자) span, .board-view th:contains(작성자) + td";
        String postDateSelector = "ul > li:contains(등록일), [class*=\"postdate\"], td.date, td.reportDate, .date, .vmode td:contains(일자) + td, .board_view_info .date, .tbl-top .info p:contains(작성일) span, .board-view th:contains(작성일) + td, .writeInfo";
        String posthitsSelector = "ul > li:contains(조회), td.hits a, td.view_cnt, .hits, .vmode td:contains(조회) + td, .board_view_info .view, .tbl-top .info p:contains(조회수) span, p.click_count, .writeInfo span";
        String postContentSelector = ".board_contents, #v_content, .v_content, .board_view_content, .board_article, .arti_content, .view_content, .view_wrap, .vmode, .tbl-cont, .view_td, .boardContent";
        
        String postlink = partialPost.getAbsoluteUrl();
        if (postlink.contains("library")) {
            titleSelector = "td.title a"; authorSelector = "td.writer"; postDateSelector = "td.reportDate"; posthitsSelector = "td.view_cnt";
        }

        Document postDoc = null;
        try {
            postDoc = htmlFetcher.getHTMLDocument(postlink);
        } catch (Exception fetchErr) {
            System.err.println("경고: 상세 페이지 로드 실패, 요약 본문 유지: " + fetchErr.getMessage());
            return;
        }

        if (postDoc != null) {
            // 상세 페이지 정보 덮어쓰기
            Element titleEl = postDoc.selectFirst(titleSelector);
            if (titleEl != null && !titleEl.text().trim().isEmpty()) partialPost.setTitle(titleEl.text().trim());

            Element authorElement = postDoc.selectFirst(authorSelector);
            if (authorElement != null) {
                String parsedAuth = authorElement.text().trim().replace("작성자 ", "").replace(" 이메일", "").split(":", 2)[0].trim();
                // 도서관 등 분할
                String[] parts = authorElement.text().trim().split(":", 2);
                if (parts.length > 1) parsedAuth = parts[1].trim();
                if (!parsedAuth.isEmpty()) partialPost.setAuthor(parsedAuth);
            }

            Element dateElement = postDoc.selectFirst(postDateSelector);
            if (dateElement != null) {
                String dateText = dateElement.text().trim();
                Matcher m = Pattern.compile("\\d{4}[-./]\\d{2}[-./]\\d{2}").matcher(dateText);
                if (m.find()) {
                    partialPost.setContentHash(m.group()); // 날짜 임시 저장 덮어쓰기
                } else {
                    String[] parts = dateText.split(":", 2);
                    String parsedDetailDate = (parts.length > 1) ? parts[1].trim() : parts[0];
                    if (!parsedDetailDate.isEmpty() && parsedDetailDate.matches(".*\\d.*")) {
                        partialPost.setContentHash(parsedDetailDate);
                    }
                }
            }

            int parsedHits = parseHits(postDoc, posthitsSelector);
            if (parsedHits >= 0) partialPost.setHits(parsedHits);

            // 본문 파싱
            Element contentArea = postDoc.selectFirst(postContentSelector);
            if (contentArea != null) {
                Elements imgTags = contentArea.select("img");
                List<String> imgs = new ArrayList<>();
                for (Element imgTag : imgTags) imgs.add(imgTag.attr("abs:src"));
                partialPost.setContentImageUrls(imgs);

                Element targetContent = contentArea.selectFirst(".sch_link_target");
                if (targetContent == null) targetContent = contentArea;
                targetContent.select("br").append("\\n");
                targetContent.select("p, div").prepend("\\n");
                targetContent.select("li").prepend("- ").append("\\n");
                targetContent.select("tr").append("\\n");
                partialPost.setContent(targetContent.text().replaceAll("\\\\n", "\n").replaceAll("\n{3,}", "\n\n").trim());
            }

            // 첨부파일 파싱
            Elements fileLinks = postDoc.select("a[href*='attach_no='], a[href*='download.php'], a[href*='file_down'], a[onclick*='file_down']");
            List<Attachment> atts = new ArrayList<>();
            for (Element link : fileLinks) {
                String url = link.attr("abs:href");
                if (url.contains("javascript:") && link.hasAttr("onclick")) url = "JS: " + link.attr("onclick");
                String name = link.text();
                if (!name.isEmpty()) atts.add(new Attachment(name, url));
            }
            partialPost.setAttachments(atts);
        }
    }

    // ----------------------------------------------------------------------
    // 3-3. URL과 게시판 경로를 대조하여 카테고리 매핑
    // ----------------------------------------------------------------------
    private void determineCategoryAndDepartment(BoardPost partialPost, List<BoardPage> pages) {
        String postlink = partialPost.getAbsoluteUrl();
        BoardConfig config = BoardConfig.getInstance();
        Category category = Category.UNIVERSITY;
        String postdepartment = null;

        if (postlink != null && !postlink.isEmpty()) {
            try {
                URI postUri = new URI(postlink);
                String postPath = postUri.getPath();
                
                for (BoardPage page : pages) {
                    URI pageUri = new URI(page.getAbsoluteUrl());
                    String pagePath = pageUri.getPath();

                    // 원래 로직 복구: path가 일치하고 host도 일치할 때
                    // 참고: sch 게시판들은 보통 /sch/06/010100.jsp 형식으로 되어 있음
                    if (postPath != null && pagePath != null && postPath.startsWith(pagePath)
                            && postUri.getAuthority() != null && pageUri.getAuthority() != null
                            && postUri.getAuthority().equals(pageUri.getAuthority())) {
                        
                        String title = page.getTitle() != null ? page.getTitle() : "";
                        String bName = page.getBoardName() != null ? page.getBoardName() : "";
                        String pTitle = partialPost.getTitle() != null ? partialPost.getTitle() : "";
                        String pContent = partialPost.getContent() != null ? partialPost.getContent() : "";
                        
                        if (config.categoryRules != null) {
                            for (BoardConfig.CategoryRule rule : config.categoryRules) {
                                boolean titleMatch = rule.titleIncludes != null && rule.titleIncludes.stream().anyMatch(title::contains);
                                
                                if ("DEPARTMENT_COMPOUND".equals(rule.matchType) && titleMatch && rule.subRules != null) {
                                    for (BoardConfig.SubRule subRule : rule.subRules) {
                                        boolean bNameMatch = subRule.boardNameIncludes != null && subRule.boardNameIncludes.stream().anyMatch(bName::contains);
                                        boolean pContentMatch = subRule.postContentIncludes != null && subRule.postContentIncludes.stream().anyMatch(kw -> pTitle.contains(kw) || pContent.contains(kw));
                                        
                                        if (bNameMatch || pContentMatch) {
                                            category = Category.valueOf(subRule.category);
                                            break;
                                        }
                                    }
                                    if (category != Category.UNIVERSITY) break;
                                } else if ("TITLE_SIMPLE".equals(rule.matchType) && titleMatch) {
                                    category = Category.valueOf(rule.category);
                                    break;
                                }
                            }
                        }
                        
                        // Fallback title to board name if not set
                        postdepartment = title != null && !title.isEmpty() ? title : bName;
                        break;
                    }
                }
            } catch (URISyntaxException e) {
                System.err.println("URL 구문 파싱 실패 (Category 결정): " + postlink);
            }
        }

        // 주소 하드코딩 Fallback (설정 파일 기반)
        if (postdepartment == null && postlink != null && config.postUrlFallbacks != null) {
            for (BoardConfig.PostUrlFallback fallback : config.postUrlFallbacks) {
                if (postlink.contains(fallback.urlContains)) {
                    postdepartment = fallback.department;
                    category = Category.valueOf(fallback.category);
                    break;
                }
            }
        }

        partialPost.setCategory(category);
        partialPost.setDepartment(postdepartment);
    }

    // ----------------------------------------------------------------------
    // 3-4. 임시 문자열 날짜를 Timestamp 객체로 묶어 최종 Post 반환
    // ----------------------------------------------------------------------
    private BoardPost buildFinalPost(BoardPost partialPost) {
        Timestamp created_at;
        String dateStr = partialPost.getContentHash(); // 임시로 날짜 문자열 저장했던 필드

        try {
            if (dateStr == null || dateStr.isEmpty()) {
                System.out.println("경고: 날짜 정보 부족으로 현재 시간 할당.");
                created_at = new Timestamp(System.currentTimeMillis());
            } else {
                LocalDate parsedDate = parseToLocalDate(dateStr);
                if (parsedDate == null) {
                    throw new DateTimeParseException("파싱할 수 없는 날짜 형식", dateStr, 0);
                }
                ZoneId koreaZoneId = ZoneId.of("Asia/Seoul");
                LocalTime timePart = LocalTime.now(koreaZoneId);
                created_at = Timestamp.valueOf(LocalDateTime.of(parsedDate, timePart));
            }
        } catch (Exception e) {
            System.err.println("시간 조합 오류 (" + dateStr + ") - 현재 시간 사용: " + partialPost.getTitle());
            created_at = new Timestamp(System.currentTimeMillis());
        }

        return new BoardPost(
            partialPost.getDepartment(),
            partialPost.getTitle(),
            partialPost.getAuthor(),
            created_at,
            partialPost.getHits(),
            partialPost.getAbsoluteUrl(),
            partialPost.getContent(),
            partialPost.getContentImageUrls() != null ? partialPost.getContentImageUrls() : new ArrayList<>(),
            partialPost.getAttachments() != null ? partialPost.getAttachments() : new ArrayList<>(),
            Category.valueOf(partialPost.getCategory()),
            "" // hash는 DBManager에서 할당
        );
    }

    private int parseHits(Document doc, String posthitsSelector) {
        String primarySelector = posthitsSelector;
        Optional<Integer> hits = tryParseWithSelector(doc, primarySelector);

        if (hits.isPresent()) {
            return hits.get();
        } else {
            String fallbackSelector = ".board_title > ul > li:nth-child(3), .type_board > tbody > tr > td:nth-child(5)";
            return tryParseWithSelector(doc, fallbackSelector).orElse(-1);
        }
    }

    private Optional<Integer> tryParseWithSelector(Document doc, String selector) {
        return Optional.ofNullable(doc.selectFirst(selector))
                .map(element -> {
                    String text = element.text().trim();
                    return text;
                })
                .map(text -> {
                    String[] parts = text.split(":", 2);
                    String numberPart = (parts.length > 1 ? parts[1] : parts[0]).trim();
                    return numberPart;
                })
                .map(rawNumber -> {
                    String digits = rawNumber.replaceAll("[^0-9]", "");
                    return digits;
                })
                .filter(digits -> {
                    if (digits.isEmpty()) {
                        return false;
                    }
                    return true;
                })
                .flatMap(this::safelyParseInt);
    }

    private Optional<Integer> safelyParseInt(String numberString) {
        try {
            return Optional.of(Integer.parseInt(numberString));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}