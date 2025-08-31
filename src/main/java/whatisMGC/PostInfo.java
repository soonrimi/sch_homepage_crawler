package whatisMGC;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**/
public class PostInfo {
    private final HtmlFetcher htmlFetcher = new HtmlFetcher();
    private final BoardInfo boardInfo = new BoardInfo();
    private LocalDate parseToLocalDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        dateString = dateString.trim();

        // HH:mm 형식 (시간만 있는 경우) -> 오늘 날짜로 처리
        if (dateString.matches("\\d{2}:\\d{2}")) {
            return LocalDate.now();
        }

        try {
            return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        } catch (DateTimeParseException e) {
            // 파싱 실패 시 다음 형식 시도
        }

        try {
            return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (DateTimeParseException e) {
            // 최종 실패
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
            try {
                System.out.println(">>> " + page.getTitle()  + "의 " + page.getBoardName() + " 게시판 탐색 시작... " + page.getAbsoluteUrl());
                String currentUrl = page.getAbsoluteUrl();
                Document currentDoc = htmlFetcher.getHTMLDocument(currentUrl);
                boolean stopCrawlingThisBoard = false;

                while (true) {
                    List<BoardPost> postsOnPage = scrapePostDetailFromBoard(currentDoc, pageList);

                    if (postsOnPage.isEmpty()) {
                        System.out.println("페이지에 더 이상 게시물이 없어 탐색을 종료합니다.");
                        break;
                    }

                    for (BoardPost post : postsOnPage) {
                        LocalDate postDate = parseToLocalDate(post.getpostDate());

                        if (postDate == null) {
                            continue;
                        }

                        // 목표 날짜(2024-01-01) 이전의 글을 만나면,
                        if (postDate.isBefore(startDate)) {
                            System.out.println(startDate + " 이전 게시물(" + post.getpostDate() + ")을 발견하여 이 게시판의 탐색을 중단합니다.");
                            stopCrawlingThisBoard = true;
                            break;
                        }

                        // 중복되지 않은 게시물만 최종 리스트에 추가합니다.
                        if (seen.add(post)) {
                            allPosts.add(post);
                        }
                    }

                    // 중단 플래그가 설정되었다면, 다음 페이지로 넘어가지 않고 while 루프를 탈출합니다.
                    if (stopCrawlingThisBoard) {
                        break;
                    }

                    // 다음 페이지로 이동하는 로직
                    String combinedSelector=".paging a[title*='다음'], .page_list a[title*='다음'],.pager a[title*='다음']";
                    Element nextElement = currentDoc.selectFirst(combinedSelector);
                    if (nextElement == null) {
                        System.out.println("마지막 페이지입니다. 이 게시판의 탐색을 종료합니다.");
                        break; // 다음 페이지가 없으면 종료합니다.
                    }

                    String nextUrl = nextElement.attr("abs:href");
                    if (nextUrl.isEmpty() || !nextUrl.startsWith("http")) {
                        System.err.println("경고: 유효하지 않은 다음 페이지 URL. 크롤링을 중단합니다: " + nextUrl);
                        break;
                    }

                    currentDoc = htmlFetcher.getHTMLDocument(nextUrl);
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
    // 2. 오늘 올라온 게시물만 가져오는 필터링 메서드
    // ======================================================================
    public List<BoardPost> getDailyPosts(List<BoardPage> pageList) throws IOException, URISyntaxException {
        Set<BoardPost> seen = new HashSet<>();
        List<BoardPost> dailyPosts = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        String todayString = today.format(formatter);
        System.out.println(todayString + "일에 올라온 글을 확인합니다.");

        for (BoardPage page : pageList) {
            System.out.println(">>> " + page.getTitle()  +"의 "+ page.getBoardName()+" 게시판의 모든 글을 탐색합니다..." +page.getAbsoluteUrl());
            try {
                String currentUrl = page.getAbsoluteUrl();
                Document currentDoc = htmlFetcher.getHTMLDocument(currentUrl);
                boolean stopCrawling = false;

                while (!stopCrawling) {
                    List<BoardPost> postsOnPage = scrapePostDetailFromBoard(currentDoc, pageList);

                    stopCrawling = true; // 기본적으로 중단으로 설정, 오늘 게시물이 있으면 false로 변경

                    for (BoardPost post : postsOnPage) {
                        try {
                            LocalDate postDate = parseToLocalDate(post.getpostDate());

                            if (postDate == null) {
                                continue;
                            }

                            if (postDate.isEqual(today)) {
                                if (seen.add(post)) {
                                    dailyPosts.add(post);
                                }
                                stopCrawling = false; // 오늘 게시물이 있으므로 계속 진행
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

                    String combinedSelector=".paging a[title*='다음'], .page_list a[title*='다음'],.pager a[title*='다음']";
                    Element nextElement = currentDoc.selectFirst(combinedSelector);
                    if (nextElement == null) {
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
                String posttitle ="";
                String postauthor = "";
                String postdate = "";
                String hits = "";


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
                        if (parts.length > 1) {
                            hits = parts[1].trim();
                        } else {
                            hits = parts[0];
                        }
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

                    int hitInt = parseHits(postDoc, posthitsSelector);
                    hits = String.valueOf(hitInt);

                }

                String content = postDoc.select(".board_contents > div.sch_link_target").text();

                String postdepartment = "중앙도서관";
                Long categoryId = 1L; // 기본값 설정
                //1 : 학사, 대학 관련, 2. 학과, 4. 채용관련, 5. 동아리 관련

                if (postlink != null && !postlink.isEmpty()) {
                    try {
                        URI postUri = new URI(postlink);
                        String postPath = postUri.getPath();
                        for (BoardPage page : pages) {
                            URI pageUri = new URI(page.getAbsoluteUrl());
                            String pagePath = pageUri.getPath();
                            if (postPath.startsWith(pagePath) && postUri.getAuthority().equals(pageUri.getAuthority())) {
                                if (page.getTitle().contains("학과") || page.getTitle().contains("학부")|| page.getTitle().contains("전공")) {
                                    if (page.getBoardName().contains("학과공지")||page.getBoardName().contains("학과소식")||page.getBoardName().contains("학과게시판")||page.getBoardName().contains("학과행사일정")||page.getBoardName().contains("학사공지")||page.getBoardName().contains("커뮤니티")){
                                        categoryId = 2L; // 학과
                                        postdepartment = page.getTitle();
                                        break;
                                }else if (page.getBoardName().contains("진로 및 취업")){
                                    categoryId = 4L; // 채용관련
                                    postdepartment = page.getTitle();
                                    break;
                                } else if (posttitle.contains("동아리") || content.contains("동아리")|| posttitle.contains("학생회")) {
                                    categoryId = 5L; // 동아리, 활동 관련
                                    postdepartment = page.getTitle();
                                    break;
                                } else {
                                    categoryId = 1L; // 기본값: 학사, 대학 관련
                                    postdepartment = page.getTitle();
                                    break;
                                }
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


                Elements fileLinks = postDoc.select("a[href*='attach_no=']");
                List<Attachment> attachments = new ArrayList<>();
                for (Element link : fileLinks) {
                    String attachmentUrl = link.attr("abs:href");
                    String attachmentName = link.text();
                    attachments.add(new Attachment(attachmentName, attachmentUrl));
                }
                postsOnPage.add(new BoardPost(postdepartment, posttitle, postauthor, postdate, hits, postlink, content, attachments,categoryId));

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