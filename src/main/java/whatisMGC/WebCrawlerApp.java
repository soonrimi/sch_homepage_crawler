package whatisMGC;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WebCrawlerApp {
    private static final String POSTS_TABLE_NAME = "crawl_posts";
    private static final String SUB_BOARDS_TABLE_NAME = "sub_crawl_pages";
    private static final String MAIN_BOARDS_TABLE_NAME = "crawl_pages";
    private static final String BASE_URL = "https://home.sch.ac.kr/sch/index.jsp";

    public static void main(String[] args) {
        // 크롤링에 필요한 객체들을 한 번만 생성합니다.
        DBManager dbManager = new DBManager();
        PostInfo postInfo = new PostInfo();
        BoardInfo boardInfo = new BoardInfo();
        HtmlFetcher htmlFetcher = new HtmlFetcher();

        while (true) {
            try {
                System.out.println("0. 테이블 존재여부 확인 및 필요한 테이블 생성");
                dbManager.createAllTablesSequentially();
                // --- 1-3단계: 게시판 목록 수집 및 저장 ---
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

                // --- 4-6단계: 게시물 크롤링 및 저장 ---
                System.out.println("4. DB에서 소게시판 목록을 가져와 게시물을 크롤링합니다...");
                List<BoardPage> subBoardsFromDb = dbManager.loadSubBoardPagesFromDb(SUB_BOARDS_TABLE_NAME);
                List<BoardPost> crawledPosts = postInfo.getAllPosts(subBoardsFromDb);
                System.out.printf("크롤링된 게시물 총 %d개를 찾았습니다.\n", crawledPosts.size());

                System.out.println("5. 기존 게시물과 중복 여부(URL + 학과)를 확인 중...");
                Set<String> existingPostKeys = dbManager.loadExistingPostKeysFromDb(POSTS_TABLE_NAME);
                List<BoardPost> newPostsToSave = crawledPosts.stream()
                        .filter(post -> !existingPostKeys.contains(post.getAbsoluteUrl() + "|" + post.getDepartment()))
                        .collect(Collectors.toList());

                if (!newPostsToSave.isEmpty()) {
                    System.out.printf("6. 새로 발견된 게시물 %d개를 DB에 저장합니다.\n", newPostsToSave.size());
                    dbManager.appendPostsToDb(newPostsToSave);
                    System.out.println("새로운 게시물 저장이 완료되었습니다.");
                } else {
                    System.out.println("새로 추가된 게시물이 없습니다.");
                }

                System.out.println("크롤링 작업이 완료되었습니다. 30분(1800초) 대기 후 다음 작업을 시작합니다...");
                Thread.sleep(1800_000); // 1,800,000ms = 30분

            } catch (Exception e) {

                System.err.println("크롤링 주기 실행 중 심각한 오류 발생: " + e.getMessage());
                e.printStackTrace(); // 오류의 상세 내용을 출력합니다.

                try {
                    System.err.println("오류 발생. 5분 후 다시 시도합니다...");
                    Thread.sleep(300_000); // 300,000ms = 5분
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("대기 중 인터럽트 발생. 프로그램을 종료합니다.");
                    break;
                }
            }
        }
    }
}
