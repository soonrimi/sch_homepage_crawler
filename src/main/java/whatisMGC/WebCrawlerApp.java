package whatisMGC;

import org.jsoup.nodes.Document;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

public class WebCrawlerApp {
    private static final String POSTS_TABLE_NAME = "board_posts";
    private static final String SUB_BOARDS_TABLE_NAME = "sub_boards";
    private static final String MAIN_BOARDS_TABLE_NAME = "board_pages";
    private static final String BASE_URL = "https://home.sch.ac.kr/sch/index.jsp";

    public static void main(String[] args) throws IOException, URISyntaxException, SQLException {
        // --- 0단계: 사용자 입력(모드) 확인 ---
        Scanner scanner = new Scanner(System.in);
        System.out.println("크롤링 모드를 입력해주세요. (all, today):");
        String mode = scanner.nextLine().toLowerCase(); // 입력을 받아 소문자로 변환
        scanner.close(); // 스캐너 리소스 정리

        DBManager dbManager = new DBManager();
        PostInfo postInfo = new PostInfo();
        BoardInfo boardInfo = new BoardInfo();
        HtmlFetcher htmlFetcher = new HtmlFetcher();

        // --- 1-3단계: 게시판 목록 수집 및 저장 (기존과 동일) ---
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

        // --- 4단계: 선택된 모드에 따라 게시물 크롤링 ---
        System.out.println("4. DB에서 소게시판 목록을 가져와 게시물을 크롤링합니다...");
        List<BoardPage> subBoardsFromDb = dbManager.loadSubBoardPagesFromDb(SUB_BOARDS_TABLE_NAME);

        List<BoardPost> crawledPosts; // 크롤링 결과를 담을 리스트

        if ("all".equals(mode)) {
            System.out.println(">> 'all' 모드로 2024년 이후 모든 게시물을 수집합니다.");
            crawledPosts = postInfo.getAllPosts(subBoardsFromDb);
        } else if ("today".equals(mode)) {
            System.out.println(">> 'today' 모드로 오늘 게시물만 수집합니다.");
            crawledPosts = postInfo.getDailyPosts(subBoardsFromDb);
        } else {
            System.out.println("오류: 잘못된 모드입니다. 'all' 또는 'today'를 사용해주세요.");
            return; // 잘못된 모드 입력 시 프로그램 종료
        }

        System.out.printf("크롤링된 게시물 총 %d개를 찾았습니다.\n", crawledPosts.size());

        // --- 5-6단계: 중복 확인 및 저장 (기존과 동일) ---
        System.out.println("5. 기존 게시물과 중복 여부(URL + 학과)를 확인 중...");
        Set<String> existingPostKeys = dbManager.loadExistingPostKeysFromDb(POSTS_TABLE_NAME);

        List<BoardPost> newPostsToSave = crawledPosts.stream()
                .filter(post -> !existingPostKeys.contains(post.getAbsoluteUrl() + "|" + post.getDepartment()))
                .collect(Collectors.toList());

        if (!newPostsToSave.isEmpty()) {
            System.out.printf("6. 새로 발견된 게시물 %d개를 DB에 저장합니다.\n", newPostsToSave.size());
            dbManager.appendPostsToDb(newPostsToSave, POSTS_TABLE_NAME);
            System.out.println("새로운 게시물 저장이 완료되었습니다.");
        } else {
            System.out.println("새로 추가된 게시물이 없습니다.");
        }
    }
}
