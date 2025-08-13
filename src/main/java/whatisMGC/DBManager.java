package whatisMGC;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import java.util.Collections;

public class DBManager {
    // MySQL 접속 정보는 실제 사용 환경에 맞게 수정하세요.
    private static final String DB_URL = "jdbc:mysql://localhost:3306/crawler?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = "root";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }
//----------------------------------------------------------------------------------------------------------------------
    /**
     * 최초 실행 시, 게시판 목록을 데이터베이스에 저장합니다.
     * `absoluteUrl`이 중복될 경우 기존 데이터를 업데이트합니다.
     * @param pages 저장할 BoardPage 객체 리스트
     * @param tableName 데이터를 저장할 테이블 이름
     * @throws SQLException 데이터베이스 연결 또는 쿼리 실행 오류 발생 시
     */
    public void saveInitialBoardPages(List<BoardPage> pages, String tableName) throws SQLException {
        String sql = "INSERT INTO " + tableName + " (title, absoluteUrl, category) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE title = VALUES(title), category = VALUES(category)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (BoardPage page : pages) {
                if (page == null) continue;
                pstmt.setString(1, page.getTitle());
                pstmt.setString(2, page.getAbsoluteUrl());
                pstmt.setString(3, page.getCategory());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
            System.out.printf("%d개의 초기 게시판 정보가 데이터베이스에 성공적으로 저장(업데이트)되었습니다.\n", pages.size());
        }
    }
    //----------------------------------------------------------------------------------------------------------------------
    public void saveSubBoards(List<BoardPage> subBoards, String tableName, BoardPage parentPage) throws SQLException {
        String sql = "INSERT INTO " + tableName + " (parent_page_title, boardName, subBoardUrl, page_id) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE subBoardUrl = VALUES(subBoardUrl)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (BoardPage subBoard : subBoards) {
                int parentId = findBoardPageIdByTitle(parentPage.getTitle());
                if (parentId == -1) {
                    System.err.printf("경고: URL '%s'에 해당하는 부모 게시판 ID를 찾을 수 없어 소게시판을 저장하지 못했습니다.\n", parentPage.getAbsoluteUrl());
                    return;
                }

                if (subBoard == null) continue;
                pstmt.setString(1, subBoard.getTitle());
                pstmt.setString(2, subBoard.getBoardName());
                pstmt.setString(3, subBoard.getAbsoluteUrl());
                pstmt.setInt(4, parentId);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        }
    }

    private int findBoardPageIdByTitle(String Title) throws SQLException {
        String sql = "SELECT id FROM Board_Pages WHERE UPPER(TRIM(title)) LIKE CONCAT(UPPER(TRIM(?)), '%')";        try (
                Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, Title);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }

    public List<BoardPage> loadMainBoardPagesFromDb(String tableName) throws SQLException {
        List<BoardPage> boardPages = new ArrayList<>();
        String sql = "SELECT id, title, absoluteUrl, category FROM " + tableName;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String title = rs.getString("title");
                String absoluteUrl = rs.getString("absoluteUrl");
                String category = rs.getString("category");
                boardPages.add(new BoardPage(title, null, absoluteUrl, category));
            }
        }
        return boardPages;
    }

    public List<BoardPage> loadSubBoardPagesFromDb(String tableName) throws SQLException {
        List<BoardPage> subBoards = new ArrayList<>();
        String sql = "SELECT B.title, S.boardName, S.subBoardUrl AS absoluteUrl, B.category " +
                "FROM board_pages AS B JOIN " + tableName + " AS S ON B.id = S.page_id";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String title = rs.getString("title");
                String boardName = rs.getString("boardName");
                String absoluteUrl = rs.getString("absoluteUrl");
                String category = rs.getString("category");
                subBoards.add(new BoardPage(title, boardName, absoluteUrl, category));
            }
        }
        return subBoards;
    }

    public Set<String> loadExistingPostUrlsFromDb(String tableName) throws SQLException {
        Set<String> existingUrls = new HashSet<>();
        String sql = "SELECT absoluteUrl FROM " + tableName;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                existingUrls.add(rs.getString("absoluteUrl"));
            }
        }
        return existingUrls;
    }

    public void appendPostsToDb(List<BoardPost> newPosts, String tableName) throws SQLException {
        String sql = "INSERT INTO " + tableName + " (department, title, author, postTime, hits, absoluteUrl, content, attachment) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (BoardPost post : newPosts) {
                if (post == null) continue;
                pstmt.setString(1, post.getDepartment());
                pstmt.setString(2, post.getTitle());
                pstmt.setString(3, post.getAuthor());
                pstmt.setString(4, post.getpostDate());
                pstmt.setString(5, post.getHits());
                pstmt.setString(6, post.getAbsoluteUrl());
                pstmt.setString(7, post.getContent());
                pstmt.setString(7, post.getAttachment());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
            System.out.printf("%d개의 게시물이 데이터베이스에 성공적으로 저장되었습니다.\n", newPosts.size());
        }
    }
}