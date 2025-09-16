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
import java.util.Set;
import java.util.stream.Collectors;

public class BoardInfo {
    private final HtmlFetcher htmlFetcher = new HtmlFetcher();

    // ----------------------------------------------------------------------
    // 웹사이트 메인 페이지에서 게시판 링크를 추출하고 카테고리를 설정하는 메서드
    // ----------------------------------------------------------------------
    public List<BoardPage> getAllPages(Document doc) {
        // 1. 공지사항 및 학과, 센터 링크들을 Jsoup 셀렉터로 모두 가져와 하나의 리스트로 합칩니다.
        List<BoardPage> allPages = new ArrayList<>();
        allPages.addAll(extractBoardInfo(".type_01 ul li > a", doc));
        allPages.addAll(extractBoardInfo(".type_02 > ul:nth-child(2) > li > a", doc));
        allPages.addAll(extractBoardInfo(".sub_6 > div:nth-child(2) > div:nth-child(1) > ul:nth-child(2) > li > a", doc));

        // 2. 카테고리 분류를 위한 이름 리스트를 정의합니다.
        List<String> centerNames = List.of("SRC센터(생활관)", "학사팀", "SW중심대학사업단", "원격교육지원센터", "국제교육교류처", "인권센터", "심리건강상담센터", "보건센터", "공학교육혁신센터", "중앙도서관", "향설나눔대학","대학일자리플러스센터(I'Design)","교원양성지원센터","Dream비교과센터");
        List<String> announceNames = List.of("대학공지", "학사공지", "장학공지", "취업정보", "입찰공고");
        List<String> exceptPage = List.of("중앙의료원", "교육혁신원", "수업시간표", "연구실안전관리", "예결산공고", "원격교육지원센터", "평생교육원", "교무처", "학생증", "SCH SDGs", "RGB CAMPUS 사업단", "COVID19공지","多Dream비교과센터","인권센터","교원양성지원센터");
        //todo 현재 인권센터, 교원양성지원센터 페이지네이션 불가로 제외함

        // 3. 제외 목록(exceptPage)에 있는 링크들을 먼저 필터링
        List<BoardPage> filteredList = allPages.stream()
                .filter(page -> exceptPage.stream().noneMatch(name -> page.getTitle().contains(name)))
                .filter(page -> !page.getAbsoluteUrl().contains("?"))
                .filter(page -> exceptPage.stream().noneMatch(name -> page.getTitle().contains(name)))
                .collect(Collectors.toList());

        List<BoardPage> subpageList = filteredList.stream()
                .map(page -> {
                    String category = "학과"; // 기본 카테고리
                    if (centerNames.stream().anyMatch(name -> page.getTitle().contains(name))) {
                        category = "센터";
                    } else if (announceNames.stream().anyMatch(name -> page.getTitle().contains(name))) {
                        category = "공지사항";
                    }
                    if (page.getTitle().contains("국제교육교류처")) {page.setAbsoluteUrl("http://sgee.sch.ac.kr/main/kor_main.php");}
                    else if(page.getTitle().contains("I'Design")){page.setAbsoluteUrl("http://id.sch.ac.kr/Main/Default.aspx");}
                    else if(page.getTitle().contains("생활관")){page.setAbsoluteUrl("https://homepage.sch.ac.kr/src");}
                    else if(page.getTitle().contains("도서관")){page.setAbsoluteUrl("https://library.sch.ac.kr/bbs/list/3");}
                    return new BoardPage(page.getTitle(), page.getBoardName(), page.getAbsoluteUrl(), category);
                })
                .collect(Collectors.toList());

        return subpageList;
    }

    // ----------------------------------------------------------------------
    /**
     * 특정 페이지(학과/카테고리 메인)에서 소게시판 링크를 찾아 BoardPage 객체로 반환합니다.
     * @return 소게시판 목록이 담긴 BoardPage 객체 리스트
     */
    // ----------------------------------------------------------------------
    public List<BoardPage> findSubBoardsOnPage(Document parentDoc, BoardPage parentPage) {
        String combinedSelector = "a:contains(커뮤니티), a:contains(공지사항),  a:contains(학사공지), a:contains(학과공지), a:contains(자료실), a:contains(게시판), a:contains(학과 취업공지),a:contains(진로 및 취업), a:contains(학과소식), a:contains(학과행사일정), a:contains(알림마당), a:contains(장학공지), a:contains(취업정보), a:contains(자유게시판), a:contains(실습 및 채용 정보), a:contains(교내장학금), a:contains(일반공지), a:contains(학술공지), a:contains(교육/행사공지), a:contains(취업정보)";
        if (parentPage.getTitle().contains("대학일자리플러스센터") || parentPage.getTitle().contains("I'Design")) {
            combinedSelector = "a:contains(재맞고 공지사항), li.liMenu:nth-child(10) > div:nth-child(2) a:contains(공지사항)";
        } else if (parentPage.getTitle().contains("보건행정경영")) {
            combinedSelector = "a:contains(공지사항), a:contains(커뮤니티), a:contains(학사공지), a:contains(학과공지), a:contains(자료실)";
        } else if (parentPage.getTitle().contains("AI빅데이터")) {
            combinedSelector = "a:contains(공지사항), a:contains(커뮤니티), a:contains(학사공지), a:contains(학과공지)";
        } else if (parentPage.getTitle().contains("공학교육혁신센터")) {
            combinedSelector = "a:contains(학사공지), a:contains(학과공지), a:contains(자료실)";
        } else if (parentPage.getTitle().contains("청소년교육상담학과")) {
            combinedSelector = "a:contains(학사공지), a:contains(학과공지), a:contains(실습 및 채용 정보)";
        } else if (parentPage.getTitle().contains("국제교육교류처")) {
            combinedSelector = "a:contains(공지사항), a:contains( 공지사항), a:contains(외국인입학 공지사항), a:contains(유학생 공지사항), a:contains(외국어강좌 공지사항), a:contains(취업·아르바이트 공지사항)";
        } else if (parentPage.getTitle().contains("환경보건학과")) {
            combinedSelector = "a:contains(공지사항)";
        }else if (parentPage.getTitle().contains("글로벌문화산업학과")) {
            combinedSelector = "a:contains(커뮤니티), a:contains(자유게시판)";
        }else if (parentPage.getTitle().contains("생활관")) {
            combinedSelector = "a:contains(공지사항), a:contains(자료실)";
        }else if (parentPage.getTitle().contains("임상병리학과")) {
            combinedSelector = "a:contains(공지사항), a:contains(자유게시판)";
        }else if (parentPage.getTitle().contains("의료IT공학과")) {
            combinedSelector = "a:contains(공지사항), a:contains(자유게시판)";
        }else if (parentPage.getTitle().contains("도서관")) {
            combinedSelector = "a:contains(일반공지), a:contains(학술공지), a:contains(교육/행사공지)";
        }else if (parentPage.getTitle().contains("전자공학과")) {
            combinedSelector = "a:contains(학과공지), a:contains(커뮤니티), a:contains(교내장학금)";
        }else if (parentPage.getTitle().contains("영미학과")) {
            combinedSelector = "a:contains(학과공지), a:contains(학과소식)";
        }else if (parentPage.getTitle().contains("중국학과")) {
            combinedSelector = "a:contains(학과공지), a:contains(학과소식)";
        }else if (parentPage.getTitle().contains("미디어커뮤니케이션학과")) {
            combinedSelector = "a:contains(학과공지), a:contains(학과소식)";
        }else if (parentPage.getTitle().contains("건축학과")) {
            combinedSelector = "a:contains(학과공지), a:contains(학과소식)";
        }else if (parentPage.getTitle().contains("스마트자동차학과")) {
            combinedSelector = "a:contains(학과공지), a:contains(학과소식)";
        }else if (parentPage.getTitle().contains("에너지공학과")) {
            combinedSelector = "a:contains(학과공지), a:contains(학과소식)";
        }else if (parentPage.getTitle().contains("공연영상학과")) {
            combinedSelector = "a:contains(학과공지), a:contains(학과소식)";
        }else if (parentPage.getTitle().contains("탄소중립학과")) {
            combinedSelector = "a:contains(학과공지), a:contains(학과소식)";
        }else if (parentPage.getTitle().contains("헬스케어융합전공")) {
            combinedSelector = "a:contains(학과공지), a:contains(학과소식)";
        }else if (parentPage.getTitle().contains("한국문화콘텐츠학과")) {
            combinedSelector = "a:contains(학과공지), a:contains(학과소식)";
        }else if (parentPage.getTitle().contains("나노화학공학과")) {
            combinedSelector = "a:contains(학과공지), a:contains(학과소식)";
        }

        Elements links = parentDoc.select(combinedSelector);

        List<BoardPage> foundBoards = new ArrayList<>();

        for (Element link : links) {
            String boardName = link.text().trim();
            String subBoardUrl = link.attr("abs:href");

            if (subBoardUrl.contains("mode=view") || subBoardUrl.contains("article_no=")) {
                continue;
            }

            if(boardName.contains("더보기")){
                continue;
            }else if(boardName.contains("-")){
                boardName=boardName.split(" ")[1];
            }else if (subBoardUrl.contains("#")) {
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
                // boardName과 category는 아직 알 수 없으므로 null로 설정
                boardPages.add(new BoardPage(title, null, absoluteUrl, null));
            }
        }
        return boardPages;
    }
}