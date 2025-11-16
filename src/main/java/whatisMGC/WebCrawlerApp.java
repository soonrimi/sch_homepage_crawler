package whatisMGC;

import org.jsoup.nodes.Document;


import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WebCrawlerApp {
    private static final String SUB_BOARDS_TABLE_NAME = "sub_crawl_pages";
    private static final String MAIN_BOARDS_TABLE_NAME = "crawl_pages";
    private static final String BASE_URL = "https://home.sch.ac.kr/sch/index.jsp";

    private static final long CRAWL_INTERVAL_MS = 30 * 60 * 1000;
    private static final long ERROR_WAIT_INTERVAL_MS = 5 * 60 * 1000;

    // 게시판 목록 동기화 로직
    private static List<BoardPage> runBoardListSync(DBManager dbManager, BoardInfo boardInfo, HtmlFetcher htmlFetcher) {
        System.out.println("\n--- [게시판 목록 동기화 시작] ---");
        try {
            System.out.println("0. 테이블 존재여부 확인 및 필요한 테이블 생성");
            dbManager.createAllTablesSequentially();

            System.out.println("1. 메인 홈페이지에서 게시판 목록을 가져와 DB에 저장합니다...");
            Document homepageDoc = htmlFetcher.getHTMLDocument(BASE_URL);
            List<BoardPage> mainBoardPages = boardInfo.getAllPages(homepageDoc);
            dbManager.saveInitialBoardPages(mainBoardPages, MAIN_BOARDS_TABLE_NAME);

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

            List<BoardPage> sub_boards = dbManager.loadSubBoardPagesFromDb(SUB_BOARDS_TABLE_NAME);
            return sub_boards;

        } catch (Exception e) {
            System.err.println("게시판 목록 동기화 중 심각한 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // 제목(Title) + 내용해시(ContentHash)를 조합하여 중복 제거 후 저장
    private static void filterAndSave(DBManager dbManager, List<BoardPost> posts) {
        if (posts == null || posts.isEmpty()) {
            System.out.println("크롤링된 게시물이 없습니다.");
            return;
        }

        System.out.println("중복 제거 필터링 시작 (기준: 제목 + 내용해시)...");

        List<BoardPost> distinctPosts = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (BoardPost post : posts) {
            if (post == null) continue;

            // 1. 해시값이 없으면 계산
            if (post.getContentHash() == null) {
                // DBManager의 calculateContentHash는 public이어야 함
                String hash = dbManager.calculateContentHash(post.getContent(), post.getContentImageUrls());
                post.setContentHash(hash);
            }

            // 2. 복합 키 생성
            String title = post.getTitle() != null ? post.getTitle() : "";
            String hash = post.getContentHash();
            String compositeKey = title + "||" + hash;

            // 3. 중복 검사 (Set에 없으면 추가)
            if (!seenKeys.contains(compositeKey)) {
                seenKeys.add(compositeKey);
                distinctPosts.add(post);
            } else {
                // 중복된 글은 저장 리스트에 담지 않음
            }
        }

        System.out.printf("필터링 결과: %d건 -> %d건 (중복 %d건 제거됨)\n",
                posts.size(), distinctPosts.size(), (posts.size() - distinctPosts.size()));

        // 4. 필터링된 리스트를 DBManager에게 전달
        if (!distinctPosts.isEmpty()) {
            dbManager.saveOrUpdatePostsInBulk(distinctPosts);
        } else {
            System.out.println("필터링 후 저장할 게시물이 없습니다.");
        }
    }

    public static void main(String[] args) {
        DBManager dbManager = new DBManager();
        PostInfo postInfo = new PostInfo();
        BoardInfo boardInfo = new BoardInfo();
        HtmlFetcher htmlFetcher = new HtmlFetcher();

        int lastFullSyncDay = -1;
        List<BoardPage> boardsToCrawl = new ArrayList<>();

        while (true) {
            try {
                ZoneId kstZoneId = ZoneId.of("Asia/Seoul");
                ZonedDateTime kstTime = ZonedDateTime.now(kstZoneId);
                int currentKstHour = kstTime.getHour();
                int currentKstDay = kstTime.getDayOfYear();

                boolean isFullSyncWindow = (currentKstHour == 7);
                boolean hasRunFullSyncToday = (lastFullSyncDay == currentKstDay);

                // 앱 시작 시 또는 KST 7시에 게시판 목록 갱신 및 전체 크롤링
                if (boardsToCrawl.isEmpty() || (isFullSyncWindow && !hasRunFullSyncToday)) {
                    boardsToCrawl = runBoardListSync(dbManager, boardInfo, htmlFetcher);

                    if (boardsToCrawl.isEmpty()) {
                        System.err.println("크롤링할 게시판 목록을 가져오지 못했습니다. 5분 후 재시도합니다.");
                        Thread.sleep(ERROR_WAIT_INTERVAL_MS);
                        continue;
                    }
                    System.out.println("4. 2024년 이후 모든 게시물을 크롤링합니다...");
                    List<BoardPost> allCrawledPosts = postInfo.getAllPosts(boardsToCrawl);
                    System.out.printf("크롤링된 게시물 총 %d개를 찾았습니다.\n", allCrawledPosts.size());

                    System.out.println("5. 수집된 모든 게시물을 DB와 비교하여 저장/업데이트합니다...");

                    filterAndSave(dbManager, allCrawledPosts);

                    lastFullSyncDay = currentKstDay;
                } else {
                    System.out.println("4. 오늘 작성된 게시물만 크롤링합니다...");
                    List<BoardPost> dailyPosts = postInfo.getDailyPosts(boardsToCrawl);
                    System.out.printf("크롤링된 오늘자 게시물 총 %d개를 찾았습니다.\n", dailyPosts.size());

                    System.out.println("5. 수집된 오늘자 게시물을 DB와 비교하여 저장/업데이트합니다...");

                    filterAndSave(dbManager, dailyPosts);
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