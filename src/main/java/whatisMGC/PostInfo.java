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


public class PostInfo {
    private final HtmlFetcher htmlFetcher = new HtmlFetcher();
    private final BoardInfo boardInfo = new BoardInfo();
    private LocalDate parseToLocalDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        dateString = dateString.trim();

        if (dateString.matches("\\d{2}:\\d{2}")) {
            return LocalDate.now();
        }

        try {
            return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        } catch (DateTimeParseException e) { /* 다음 시도 */ }

        try {
            return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (DateTimeParseException e) {
            System.err.println("경고: 지원하지 않는 날짜 형식입니다. '" + dateString + "'");
            return null;
        }
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
            if ("자유게시판".equals(page.getBoardName()) || "학사공지".equals(page.getBoardName())){
                continue;
            }
            try {
                System.out.println(">>> " + page.getTitle()  + "의 " + (page.getBoardName() != null ? page.getBoardName() : "(메인)") + " 게시판 탐색 시작... " + page.getAbsoluteUrl());                String currentUrl = page.getAbsoluteUrl();
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


                    // 다음 페이지로 이동하는 로직 (기존과 동일)
                    String combinedSelector=".paging a[title*='다음'], .page_list a[title*='다음'],.pager a[title*='다음'], a.pager.next:has(span:contains(다음)), a.pager.next, a:has(img[alt*='다음'])";
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
                        long delay = 500 + (long)(Math.random() * 500);
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    currentDoc = htmlFetcher.getHTMLDocument(nextUrl);
                }
                try {
                    long delay = 1000 + (long)(Math.random() * 1000);
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
            if ("자유게시판".equals(page.getBoardName()) || "학사공지".equals(page.getBoardName())){
                continue;
            }
            try {
                System.out.println(">>> [오늘] " + page.getTitle()  +"의 "+ (page.getBoardName() != null ? page.getBoardName() : "(메인)")+" 게시판의 모든 글을 탐색합니다... " +page.getAbsoluteUrl());
                String currentUrl = page.getAbsoluteUrl();
                Document currentDoc = htmlFetcher.getHTMLDocument(currentUrl);
                boolean stopCrawling = false;

                while (!stopCrawling) {
                    List<BoardPost> postsOnPage = scrapePostDetailFromBoard(currentDoc, pageList);
                    if (postsOnPage.isEmpty()) {
                        System.out.println(" - 페이지에 더 이상 게시물이 없어 탐색을 종료합니다.");
                        break; // 이 페이지에 글 없음, 페이징 중단
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
                            // 어제 날짜 글을 만나면 중단
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

                    String combinedSelector=".paging a[title*='다음'], .page_list a[title*='다음'],.pager a[title*='다음']";
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
        Elements posts = doc.select(".type_board tbody tr,.listTable tbody tr");
        String combinedSelector = "td.subject a, td  a[href*='article_no='],li a[href*='board_no='], .listTable tr td a, td.title a";
        for (Element post : posts) {

            Element linkElement = post.selectFirst(combinedSelector);
            if (linkElement == null) {
                // 게시물이 없는 경우 (예: "등록된 글이 없습니다." 행) 건너뜀
                continue;
            }

            try {
                String postlink = linkElement.attr("abs:href");
                String titleSelector=".board_title > :is(h1, h2, h3, h4, h5, h6), .board_title > :is(h1, h2, h3, h4, h5, h6), td.title a, type_board tbody [class*=\"subject\"], .subject";
                String authorSelector="ul > li:contains(작성자), [class*=\"writer\"],  td.writer, .writer";
                String postDateSelector= "ul > li:contains(등록일), [class*=\"postdate\"], td.date, td.reportDate, .date";
                String posthitsSelector="ul > li:contains(조회),  td.hits a, td.view_cnt, .hits";
                String postContentSelector = ".board_contents";
                String posttitle ="";
                String postauthor = "";
                String postdate = "";
                Integer hitInt = 0;
                String content = "";



                if (postlink.contains("library")){
                    titleSelector="td.title a";
                    authorSelector="td.writer";
                    postDateSelector= "td.reportDate";
                    posthitsSelector="td.view_cnt";
                }


                Document postDoc = htmlFetcher.getHTMLDocument(postlink);
                if (postDoc == null) {
                    continue; // 상세 페이지를 가져오지 못하면 건너뜁니다.
                }
                if (postlink.contains("library")) {
                    // --- 게시물의 공통 정보를 안전하게 가져옵니다. ---
                    postlink = linkElement.attr("abs:href");

                    posttitle = post.selectFirst(titleSelector).text();

                    //  작성자
                    Element authorElement = post.selectFirst(authorSelector);
                    if (authorElement != null) {
                        String authorText = authorElement.text().trim();
                        String[] parts = authorText.split(":", 2);
                        if (parts.length > 1) {
                            postauthor = parts[1].trim();
                        } else {
                            postauthor = parts[0];
                        }
                    }

                    //  등록일
                    Element dateElement = post.selectFirst(postDateSelector);
                    if (dateElement != null) {
                        String dateText = dateElement.text().trim();
                        String[] parts = dateText.split(":", 2);
                        if (parts.length > 1) {
                            postdate = parts[1].trim();
                        } else {
                            postdate = parts[0];
                        }
                    }

                    //  조회수
                    Element hitsElement = post.selectFirst(posthitsSelector);
                    if (hitsElement != null) {
                        String hitsText = hitsElement.text().trim();
                        String[] parts = hitsText.split(":", 2);
                        String hitsStr = (parts.length > 1) ? parts[1].trim() : parts[0];
                        hitInt = safelyParseInt(hitsStr.replaceAll("[^0-9]", "")).orElse(0);
                    }
                }else {
                    // --- 게시물의 공통 정보를 안전하게 가져옵니다. ---
                    posttitle = postDoc.selectFirst(titleSelector).text();

                    //  작성자

                    Element authorElement = postDoc.selectFirst(authorSelector);
                    if (authorElement != null) {
                        String authorText = authorElement.text().trim();
                        String[] parts = authorText.split(":", 2);
                        if (parts.length > 1) {
                            postauthor = parts[1].trim();
                        } else {
                            postauthor = parts[0];
                        }
                    }

                    //  등록일
                    Element dateElement = postDoc.selectFirst(postDateSelector);
                    if (dateElement != null) {
                        String dateText = dateElement.text().trim();
                        String[] parts = dateText.split(":", 2);
                        if (parts.length > 1) {
                            postdate = parts[1].trim();
                        } else {
                            postdate = parts[0];
                        }
                    }

                    //  조회수

                    hitInt = parseHits(postDoc, posthitsSelector);

                }


                //게시물, 게시물 내 이미지
                Element contentArea = postDoc.selectFirst(postContentSelector);
                List<String> contentImageUrls = new ArrayList<>();

                if (contentArea != null) {
                    Elements imgTags = contentArea.select("img");
                    for (Element imgTag : imgTags) {
                        contentImageUrls.add(imgTag.attr("abs:src"));
                    }

                    Element brBasedContent = contentArea.selectFirst(".sch_link_target");

                    if (brBasedContent != null) {
                        brBasedContent.select("br").forEach(br -> br.after("\n"));
                        content = brBasedContent.text();
                    } else {
                        Elements paragraphs = contentArea.select("p");
                        StringBuilder contentBuilder = new StringBuilder();
                        for (Element p : paragraphs) {
                            String line = p.text();
                            contentBuilder.append(line).append("\n");
                        }
                        content = contentBuilder.toString();
                    }
                }

                String postdepartment = "중앙도서관";
                Category category = Category.UNIVERSITY; // 기본값 설정
                //1 : 학사, 대학 관련, 2. 학과, 4. 채용관련, 5. 동아리 관련

                if (postlink != null && !postlink.isEmpty()) {
                    try {
                        URI postUri = new URI(postlink);
                        String postPath = postUri.getPath();
                        for (BoardPage page : pages) {
                            URI pageUri = new URI(page.getAbsoluteUrl());
                            String pagePath = pageUri.getPath();

                            if (postPath.startsWith(pagePath) && postUri.getAuthority().equals(pageUri.getAuthority())) {
                                if (page.getTitle().contains("학과") || page.getTitle().contains("학부") || page.getTitle().contains("전공")) {
                                    if (page.getBoardName().contains("학과공지") || page.getBoardName().contains("학과소식") || page.getBoardName().contains("학과게시판") || page.getBoardName().contains("학과행사일정") || page.getBoardName().contains("커뮤니티")) {
                                        category = Category.DEPARTMENT; // 학과
                                        postdepartment = page.getTitle();
                                        break;
                                    } else if (page.getBoardName().contains("진로 및 취업")) {
                                        category = Category.RECRUIT; // 채용관련
                                        postdepartment = page.getTitle();
                                        break;
                                    } else if (posttitle.contains("동아리") || content.contains("동아리") || posttitle.contains("학생회")) {
                                        category = Category.ACTIVITY; // 동아리, 활동 관련
                                        postdepartment = page.getTitle();
                                        break;
                                    } else {
                                        postdepartment = page.getTitle();
                                        break;
                                    }
                                } else if (page.getTitle().contains("채용") || page.getTitle().contains("취업") || page.getTitle().contains("진로")) {
                                    category = Category.RECRUIT; // 채용관련
                                    postdepartment = page.getTitle();
                                    break;
                                } else if (page.getTitle().contains("동아리") || page.getTitle().contains("학생회") || page.getTitle().contains("총학생회")) {
                                    category = Category.ACTIVITY; // 동아리, 활동 관련
                                    postdepartment = page.getTitle();
                                    break;
                                } else if (page.getTitle().contains("홍보") || page.getTitle().contains("광고") || page.getTitle().contains("PR")) {
                                    category = Category.PROMOTION; // 홍보, 광고 관련
                                    postdepartment = page.getTitle();
                                    break;
                                } else {
                                    postdepartment = page.getTitle();
                                    break;
                                }
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
                Timestamp created_at;
                try {
                    LocalDate parsedDate = parseToLocalDate(postdate);
                    if(parsedDate == null) {
                        throw new DateTimeParseException("파싱할 수 없는 날짜 형식", postdate, 0);
                    }
                    ZoneId koreaZoneId = ZoneId.of("Asia/Seoul");
                    LocalTime timePart = LocalTime.now(koreaZoneId);
                    created_at = Timestamp.valueOf(LocalDateTime.of(parsedDate, timePart));
                } catch (Exception e) {
                    System.err.println("경고: 날짜/시간 조합 실패로 현재 시간 사용: " + postdate + " (글: " + posttitle + ") - " + e.getMessage());
                    created_at = new Timestamp(System.currentTimeMillis()); // 파싱 실패 시 현재 시간
                }

                Elements fileLinks = postDoc.select("a[href*='attach_no=']");
                List<Attachment> attachments = new ArrayList<>();
                for (Element link : fileLinks) {
                    String attachmentUrl = link.attr("abs:href");
                    String attachmentName = link.text();
                    attachments.add(new Attachment(attachmentName, attachmentUrl));
                }
                postsOnPage.add(new BoardPost(postdepartment, posttitle, postauthor, created_at, hitInt, postlink, content, contentImageUrls ,attachments,category, ""));
            } catch (Exception e) {
                System.err.println("오류: 게시물 상세 페이지 처리 중 예외가 발생했습니다: " + e.getMessage() + " (URL: " + post + ")");
            }
        }
        return postsOnPage;
    }
    private int parseHits(Document doc, String posthitsSelector) {
        // 1. 첫 번째 CSS 선택자로 시도
        String primarySelector = posthitsSelector;
        Optional<Integer> hits = tryParseWithSelector(doc, primarySelector);

        // 2. 첫 번째 방법이 실패하면, 두 번째 선택자로 시도
        if (hits.isPresent()) {
            return hits.get();
        } else {
            String fallbackSelector = ".board_title > ul > li:nth-child(3), .type_board > tbody > tr > td:nth-child(5)";
            return tryParseWithSelector(doc, fallbackSelector).orElse(-1);
        }
    }

    /**
     * 특정 CSS 선택자를 사용해 엘리먼트를 찾고, 텍스트를 파싱하여 숫자로 변환합니다.
     * @param doc 파싱할 Jsoup Document 객체
     * @param selector 사용할 CSS 선택자
     * @return 성공 시 Optional<Integer>, 엘리먼트가 없거나 파싱 실패 시 Optional.empty()
     */
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