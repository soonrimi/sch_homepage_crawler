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

    private static List<BoardPage> runBoardListSync(DBManager dbManager, BoardInfo boardInfo, HtmlFetcher htmlFetcher) {
        System.out.println("\n--- [게시판 목록 동기화 시작] ---");
        try {
            dbManager.createAllTablesSequentially();

            Document homepageDoc = htmlFetcher.getHTMLDocument(BASE_URL);
            List<BoardPage> mainBoardPages = boardInfo.getAllPages(homepageDoc);
            dbManager.saveInitialBoardPages(mainBoardPages, MAIN_BOARDS_TABLE_NAME);

            List<BoardPage> pagesFromDb = dbManager.loadMainBoardPagesFromDb(MAIN_BOARDS_TABLE_NAME);

            for (BoardPage page : pagesFromDb) {
                try {
                    Document subBoardDoc = htmlFetcher.getHTMLDocument(page.getAbsoluteUrl());
                    List<BoardPage> subBoards = boardInfo.findSubBoardsOnPage(subBoardDoc, page);
                    if (!subBoards.isEmpty()) {
                        dbManager.saveSubBoards(subBoards, SUB_BOARDS_TABLE_NAME, page);
                    }
                } catch (Exception e) {
                    System.err.println("오류: " + page.getTitle() + " - " + e.getMessage());
                }
            }
            dbManager.insertHardcodedLibraryBoards(SUB_BOARDS_TABLE_NAME);

            return dbManager.loadSubBoardPagesFromDb(SUB_BOARDS_TABLE_NAME);

        } catch (Exception e) {
            System.err.println("게시판 목록 동기화 실패: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static void filterAndSave(DBManager dbManager, List<BoardPost> posts) {
        if (posts == null || posts.isEmpty()) {
            System.out.println("크롤링된 게시물이 없습니다.");
            return;
        }

        List<BoardPost> distinctPosts = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (BoardPost post : posts) {
            if (post == null) continue;

            if (post.getContentHash() == null) {
                String hash = dbManager.calculateContentHash(post.getContent(), post.getContentImageUrls());
                post.setContentHash(hash);
            }

            String title = post.getTitle() != null ? post.getTitle() : "";
            String hash = post.getContentHash();
            String compositeKey = title + "||" + hash;

            if (!seenKeys.contains(compositeKey)) {
                seenKeys.add(compositeKey);
                distinctPosts.add(post);
            }
        }

        System.out.printf("필터링: %d건 -> %d건 (중복 %d건)\n", posts.size(), distinctPosts.size(), (posts.size() - distinctPosts.size()));

        if (!distinctPosts.isEmpty()) {
            dbManager.saveOrUpdatePostsInBulk(distinctPosts);
        }
    }

    public static void main(String[] args) {
        DBManager dbManager = new DBManager();
        PostInfo postInfo = new PostInfo();
        BoardInfo boardInfo = new BoardInfo();
        HtmlFetcher htmlFetcher = new HtmlFetcher();

        List<BoardPage> boardsToCrawl = new ArrayList<>();

        System.out.println("\n>>> [크롤러 시작] 게시판 목록 초기화 진행...");
        boardsToCrawl = runBoardListSync(dbManager, boardInfo, htmlFetcher);

        if (boardsToCrawl.isEmpty()) {
            System.err.println("게시판 목록 확보 실패. 프로그램을 종료합니다.");
            return;
        }
        System.out.println("게시판 목록 로드 완료: " + boardsToCrawl.size() + "개");

        while (true) {
            try {
                ZoneId kstZoneId = ZoneId.of("Asia/Seoul");
                int currentKstHour = ZonedDateTime.now(kstZoneId).getHour();

                if (currentKstHour < 9) {
                    System.out.println("\n>>>  '전체 게시물' 크롤링 (2024~)");
                    List<BoardPost> allCrawledPosts = postInfo.getAllPosts(boardsToCrawl);
                    filterAndSave(dbManager, allCrawledPosts);
                } else {
                    System.out.println("\n>>>'오늘 게시물' 크롤링");
                    List<BoardPost> dailyPosts = postInfo.getDailyPosts(boardsToCrawl);
                    filterAndSave(dbManager, dailyPosts);
                }

                System.out.printf("\n작업 완료. %d분 대기...\n", (CRAWL_INTERVAL_MS / 60000));
                Thread.sleep(CRAWL_INTERVAL_MS);

            } catch (Exception e) {
                System.err.println("메인 루프 오류: " + e.getMessage());
                e.printStackTrace();
                try {
                    Thread.sleep(ERROR_WAIT_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}