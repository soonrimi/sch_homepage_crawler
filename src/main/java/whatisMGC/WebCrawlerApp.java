package whatisMGC;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList; // [추가]
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WebCrawlerApp {
    private static final String SUB_BOARDS_TABLE_NAME = "sub_crawl_pages";
    private static final String MAIN_BOARDS_TABLE_NAME = "crawl_pages";
    private static final String BASE_URL = "https://home.sch.ac.kr/sch/index.jsp";

    private static final long CRAWL_INTERVAL_MS = 30 * 60 * 1000;
    private static final long ERROR_WAIT_INTERVAL_MS = 5 * 60 * 1000;

    //게시판 목록 동기화 로직을 별도 메서드로 분리 (효율화)
    private static List<BoardPage> runBoardListSync(DBManager dbManager, BoardInfo boardInfo, HtmlFetcher htmlFetcher) {
        System.out.println("\n--- [게시판 목록 동기화 시작] ---");
        try {
            // 0. 테이블 스키마 검사 (매번 실행)
            System.out.println("0. 테이블 존재여부 확인 및 필요한 테이블 생성");
            dbManager.createAllTablesSequentially();

            // 1. 메인 홈페이지에서 게시판 목록 가져와 DB에 저장
            System.out.println("1. 메인 홈페이지에서 게시판 목록을 가져와 DB에 저장합니다...");
            Document homepageDoc = htmlFetcher.getHTMLDocument(BASE_URL);
            List<BoardPage> mainBoardPages = boardInfo.getAllPages(homepageDoc);
            dbManager.saveInitialBoardPages(mainBoardPages, MAIN_BOARDS_TABLE_NAME);

            // 2. DB에서 메인 게시판 목록 가져와 소게시판 탐색
            System.out.println("2. DB에서 메인 게시판 목록을 가져와 소게시판을 찾고 있습니다...");
            List<BoardPage> pagesFromDb = dbManager.loadMainBoardPagesFromDb(MAIN_BOARDS_TABLE_NAME);

            System.out.println("3. 소게시판 목록을 DB에 저장합니다...");
            for (BoardPage page : pagesFromDb) {
                try {
                    Document subBoardDoc = htmlFetcher.getHTMLDocument(page.getAbsoluteUrl());
                    List<BoardPage> subBoards = boardInfo.findSubBoardsOnPage(subBoardDoc, page);
                    if (!subBoards.isEmpty()) {
                        dbManager.saveSubBoards(subBoards, SUB_BOARDS_TABLE_NAME, page);
                    }
                } catch (Exception e) {
                    System.err.println("오류: " + page.getTitle() + "의 소게시판 처리 중 문제 발생 - " + e.getMessage());
                }
            }
            dbManager.insertHardcodedLibraryBoards(SUB_BOARDS_TABLE_NAME);

            List<BoardPage> sub_boards =dbManager.loadSubBoardPagesFromDb(SUB_BOARDS_TABLE_NAME);
            return sub_boards;

        } catch (Exception e) {
            System.err.println("게시판 목록 동기화 중 심각한 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>(); // 오류 발생 시 빈 리스트 반환
        }
    }


    public static void main(String[] args) {
        DBManager dbManager = new DBManager();
        PostInfo postInfo = new PostInfo();
        BoardInfo boardInfo = new BoardInfo();
        HtmlFetcher htmlFetcher = new HtmlFetcher();

        int lastFullSyncDay = -1;

        //  크롤링할 게시판 목록을 루프 밖에서 관리
        List<BoardPage> boardsToCrawl = new ArrayList<>();

        while (true) {
            try {
                ZoneId kstZoneId = ZoneId.of("Asia/Seoul");
                ZonedDateTime kstTime = ZonedDateTime.now(kstZoneId);
                int currentKstHour = kstTime.getHour();
                int currentKstDay = kstTime.getDayOfYear();

                boolean isFullSyncWindow = (currentKstHour == 7);
                boolean hasRunFullSyncToday = (lastFullSyncDay == currentKstDay);

                //  앱 시작 시, KST 7시에만 게시판 목록 갱신
                if (boardsToCrawl.isEmpty() || (isFullSyncWindow && !hasRunFullSyncToday)) {
                    // 게시판 목록 갱신 (1~3단계)
                    boardsToCrawl = runBoardListSync(dbManager, boardInfo, htmlFetcher);

                    if (boardsToCrawl.isEmpty()) {
                        System.err.println("크롤링할 게시판 목록을 가져오지 못했습니다. 5분 후 재시도합니다.");
                        Thread.sleep(ERROR_WAIT_INTERVAL_MS);
                        continue;
                    }
                    System.out.println("4.  2024년 이후 모든 게시물을 크롤링합니다...");
                    List<BoardPost> allCrawledPosts = postInfo.getAllPosts(boardsToCrawl);
                    System.out.printf("크롤링된 게시물 총 %d개를 찾았습니다.\n", allCrawledPosts.size());

                    System.out.println("5. 수집된 모든 게시물을 DB와 비교하여 저장/업데이트합니다...");
                    if (!allCrawledPosts.isEmpty()) {
                        dbManager.saveOrUpdatePostsInBulk(allCrawledPosts);
                    } else {
                        System.out.println("크롤링된 게시물이 없어 DB 작업을 건너<0xEB><0x9B><0xB0>니다.");
                    }

                    lastFullSyncDay = currentKstDay;
                } else {
                    System.out.println("4. 오늘 작성된 게시물만 크롤링합니다...");
                    List<BoardPost> dailyPosts = postInfo.getDailyPosts(boardsToCrawl);
                    System.out.printf("크롤링된 오늘자 게시물 총 %d개를 찾았습니다.\n", dailyPosts.size());

                    System.out.println("5. 수집된 오늘자 게시물을 DB와 비교하여 저장/업데이트합니다...");
                    if (!dailyPosts.isEmpty()) {
                        dbManager.saveOrUpdatePostsInBulk(dailyPosts);
                    } else {
                        System.out.println("새로 발견된 오늘자 게시물이 없습니다.");
                    }
                }

                if (currentKstHour == 0 && hasRunFullSyncToday) {
                    System.out.println("\nKST 자정이 되어 전체 동기화 플래그를 초기화합니다.");
                    lastFullSyncDay = -1;
                }

                System.out.printf("\n크롤링 작업이 완료되었습니다. %d분 대기 후 다음 주기를 시작합니다...\n", (CRAWL_INTERVAL_MS / 60000));
                Thread.sleep(CRAWL_INTERVAL_MS);

            } catch (Exception e) {
                System.err.println("크롤링 주기 실행 중 심각한 오류 발생: " + e.getMessage());
                e.printStackTrace();
                try {
                    System.err.printf("오류 발생. %d분 후 다시 시도합니다...\n", (ERROR_WAIT_INTERVAL_MS / 60000));
                    Thread.sleep(ERROR_WAIT_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("대기 중 인터럽트 발생. 프로그램을 종료합니다.");
                    break;
                }
            }
        }
    }
}