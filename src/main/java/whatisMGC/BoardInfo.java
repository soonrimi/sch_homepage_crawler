package whatisMGC;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BoardInfo {
    private final HtmlFetcher htmlFetcher = new HtmlFetcher();

    private static final String DEFAULT_SUBBOARD_SELECTOR = "a:contains(커뮤니티), a:contains(공지사항), a:contains(학사공지), a:contains(학과공지), a:contains(자료실), a:contains(게시판), a:contains(학과 취업공지), a:contains(진로 및 취업), a:contains(학과소식), a:contains(학과행사일정), a:contains(알림마당), a:contains(장학공지), a:contains(취업정보), a:contains(자유게시판), a:contains(실습 및 채용 정보), a:contains(교내장학금), a:contains(일반공지), a:contains(학술공지), a:contains(교육/행사공지)";

    // ----------------------------------------------------------------------
    // 웹사이트 메인 페이지에서 게시판 링크를 추출하고 카테고리를 설정하는 메서드
    // ----------------------------------------------------------------------
    public List<BoardPage> getAllPages(Document doc) {
        // 1. 공지사항 및 학과, 센터 링크들을 Jsoup 셀렉터로 모두 가져와 하나의 리스트로 합칩니다.
        List<BoardPage> allPages = new ArrayList<>();
        allPages.addAll(
                extractBoardInfo(".sub_6 > div:nth-child(2) > div:nth-child(1) > ul:nth-child(2) > li > a", doc));
        allPages.addAll(extractBoardInfo(".type_02 > ul:nth-child(2) > li > a", doc));
        allPages.addAll(extractBoardInfo(".type_01 ul li > a", doc));

        BoardConfig config = BoardConfig.getInstance();

        // 2. 제외 대상 필터링
        List<BoardPage> filteredList = allPages.stream()
                .filter(page -> config.excludedPages != null
                        && config.excludedPages.stream().noneMatch(name -> page.getTitle().contains(name)))
                .filter(page -> !page.getAbsoluteUrl().contains("?"))
                .collect(Collectors.toList());

        // 3. 카테고리 분류 및 예외 URL 매핑
        List<BoardPage> subpageList = filteredList.stream()
                .map(page -> {
                    String category = "학과"; // 기본 카테고리
                    if (config.centerNames != null
                            && config.centerNames.stream().anyMatch(name -> page.getTitle().contains(name))) {
                        category = "센터";
                    } else if (config.announceNames != null
                            && config.announceNames.stream().anyMatch(name -> page.getTitle().contains(name))) {
                        category = "공지사항";
                    }

                    // URL 오버라이드 룰 로드
                    if (config.subpageUrlOverrides != null) {
                        for (BoardConfig.SubpageUrlOverride override : config.subpageUrlOverrides) {
                            if (page.getTitle().contains(override.keyword)) {
                                page.setAbsoluteUrl(override.url);
                                break;
                            }
                        }
                    }

                    return new BoardPage(page.getTitle(), page.getBoardName(), page.getAbsoluteUrl(), category);
                })
                .collect(Collectors.toList());

        return subpageList;
    }

    // ----------------------------------------------------------------------
    /**
     * 특정 페이지(학과/카테고리 메인)에서 소게시판 링크를 찾아 BoardPage 객체로 반환합니다.
     * 
     * @return 소게시판 목록이 담긴 BoardPage 객체 리스트
     */
    // ----------------------------------------------------------------------
    public List<BoardPage> findSubBoardsOnPage(Document parentDoc, BoardPage parentPage) {
        BoardConfig config = BoardConfig.getInstance();

        String combinedSelector = DEFAULT_SUBBOARD_SELECTOR;
        if (config.customSelectors != null) {
            combinedSelector = config.customSelectors.entrySet().stream()
                    .filter(entry -> parentPage.getTitle().contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(DEFAULT_SUBBOARD_SELECTOR);
        }

        Elements links = parentDoc.select(combinedSelector);

        List<BoardPage> foundBoards = new ArrayList<>();

        for (Element link : links) {
            String boardName = link.text().trim();
            String subBoardUrl = link.attr("abs:href");

            if (subBoardUrl.contains("mode=view") || subBoardUrl.contains("article_no=")) {
                continue;
            }

            if (boardName.contains("더보기")) {
                continue;
            } else if (boardName.contains("-")) {
                boardName = boardName.split(" ")[1];
            } else if (subBoardUrl.contains("#")) {
                continue;
            }
            if (!subBoardUrl.isEmpty()) {
                foundBoards.add(new BoardPage(parentPage.getTitle(), boardName, subBoardUrl, parentPage.getCategory()));
            }
        }

        return foundBoards;
    }

    // ----------------------------------------------------------------------
    // Jsoup 셀렉터로 링크를 추출하는 헬퍼 메서드
    // ----------------------------------------------------------------------
    public List<BoardPage> extractBoardInfo(String selector, Document doc) {
        List<BoardPage> boardPages = new ArrayList<>();
        Elements links = doc.select(selector);

        if (links.isEmpty()) {
            System.err.println("경고: 지정된 셀렉터로 링크를 찾을 수 없습니다: " + selector);
            return boardPages;
        }

        for (Element link : links) {
            String title = link.text().trim();
            String absoluteUrl = link.attr("abs:href");
            if (!title.isEmpty() && !absoluteUrl.isEmpty()) {
                // boardName과 category는 탐색 이후 과정에서 할당하므로 초기에는 null을 줍니다.
                boardPages.add(new BoardPage(title, null, absoluteUrl, null));
            }
        }
        return boardPages;
    }
}