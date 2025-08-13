// WebCrawlerApp.java
package whatisMGC;

import org.jsoup.nodes.Document;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WebCrawlerApp {
    private static final String POSTS_TABLE_NAME = "board_posts";
    private static final String SUB_BOARDS_TABLE_NAME = "sub_boards";
    private static final String MAIN_BOARDS_TABLE_NAME = "board_pages";
    private static final String BASE_URL = "https://home.sch.ac.kr/sch/index.jsp";

    public static void main(String[] args) throws IOException, URISyntaxException, SQLException {
        DBManager dbManager = new DBManager();
        PostInfo postInfo = new PostInfo();
        BoardInfo boardInfo = new BoardInfo();
        HtmlFetcher htmlFetcher = new HtmlFetcher();

// 메인 홈페이지에서 게시판 목록 가져오기 및 DB 저장
        System.out.println("1. 메인 홈페이지에서 게시판 목록을 가져와 DB에 저장합니다...");
        Document homepageDoc = htmlFetcher.getHTMLDocument(BASE_URL);
        List<BoardPage> mainBoardPages = boardInfo.getAllPages(homepageDoc);
        dbManager.saveInitialBoardPages(mainBoardPages, MAIN_BOARDS_TABLE_NAME);

        // 2. DB에서 메인 게시판 목록을 가져와 소게시판 찾기
        System.out.println("2. DB에서 메인 게시판 목록을 가져와 소게시판을 찾고 있습니다...");
        List<BoardPage> pagesFromDb = dbManager.loadMainBoardPagesFromDb(MAIN_BOARDS_TABLE_NAME);

        // 3. 각 메인 게시판 페이지에서 소게시판을 추출하고 DB에 저장
        System.out.println("3. 소게시판 목록을 DB에 저장합니다...");
        for (BoardPage page : pagesFromDb) {
            try { // <-- 1. try 블록 시작
                Document subBoardDoc = htmlFetcher.getHTMLDocument(page.getAbsoluteUrl());
                List<BoardPage> subBoards = boardInfo.findSubBoardsOnPage(subBoardDoc, page);

                // 현재 페이지(page)에서 찾은 소게시판만 필터링 및 저장
                List<BoardPage> filteredSubBoards = new ArrayList<>();
                for (BoardPage subBoard : subBoards) {
                    if (subBoard.getAbsoluteUrl().contains(page.getAbsoluteUrl())) {
                        subBoard.setTitle(page.getTitle());
                        filteredSubBoards.add(subBoard);
                    }
                }

                // 2. DB 저장을 try 블록 안으로 이동
                if (!filteredSubBoards.isEmpty()) {
                    dbManager.saveSubBoards(filteredSubBoards, SUB_BOARDS_TABLE_NAME, page);
                }

            } catch (Exception  e) {
                System.out.println(e.getMessage());
            }
        }
        // 4. DB에서 소게시판 목록을 가져와 게시물 크롤링
        System.out.println("4. DB에서 소게시판 목록을 가져와 게시물을 크롤링합니다...");
        List<BoardPage> subBoardsFromDb = dbManager.loadSubBoardPagesFromDb(SUB_BOARDS_TABLE_NAME);
//2개중 선택해서 받기


//        List<BoardPost> postsToSave = postInfo.getAllPosts(subBoardsFromDb);
        List<BoardPost> postsToSave = postInfo.getDailyPosts(subBoardsFromDb);

        System.out.printf("오늘 작성된 게시물 총 %d개를 찾았습니다.\n", postsToSave.size());

        // 5. 기존 게시물과 중복 여부 확인
        System.out.println("5. 기존 게시물과 중복 여부 확인 중...");
        Set<String> existingUrls = dbManager.loadExistingPostUrlsFromDb(POSTS_TABLE_NAME);

        List<BoardPost> newPostsToSave = postsToSave.stream()
                .filter(post -> !existingUrls.contains(post.getAbsoluteUrl()))
                .collect(Collectors.toList());

        // 6. 새로운 게시물 DB에 저장
        if (!newPostsToSave.isEmpty()) {
            System.out.printf("6. 새로 발견된 게시물 %d개를 DB에 저장합니다.\n", newPostsToSave.size());
            for (BoardPage page:pagesFromDb){
                for (BoardPost post:newPostsToSave){
                    if (post.getAbsoluteUrl().contains(page.getAbsoluteUrl())){
                        post.setDepartment(page.getTitle());
                        dbManager.appendPostsToDb(newPostsToSave, POSTS_TABLE_NAME);

                    }
                }
            }
            System.out.println(newPostsToSave);
        } else {
            System.out.println("새로 추가된 게시물이 없습니다.");
        }
    }
}