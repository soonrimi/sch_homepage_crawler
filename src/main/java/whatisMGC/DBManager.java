package whatisMGC;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;

public class DBManager {
    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASS;

    static {
        DB_URL = DBConfig.DB_URL;
        DB_USER = DBConfig.DB_USER;
        DB_PASS = DBConfig.DB_PASSWORD;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
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
        String sql = "INSERT INTO " + tableName + " (title, absolute_url, category) VALUES (?, ?, ?) " +
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
        String sql = "INSERT INTO " + tableName + " (parent_page_title, board_name, sub_board_url, page_id) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE sub_board_url = VALUES(sub_board_url)";
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

    /**
     * 중앙도서관의 '일반공지'와 '학술공지' 데이터를 데이터베이스에 강제로 삽입합니다.
     * 이 메서드는 주로 애플리케이션 초기 설정 시 사용됩니다.
     * @param tableName 데이터를 삽입할 소게시판 테이블 이름
     */
    public void insertHardcodedLibraryBoards(String tableName) {
        // ON DUPLICATE KEY UPDATE를 사용하여 이미 데이터가 있어도 오류 없이 URL을 업데이트합니다.
        String sql = "INSERT INTO " + tableName + " (parent_page_title, board_name, sub_board_url, page_id) VALUES " +
                "('중앙도서관', '일반공지', 'https://library.sch.ac.kr/bbs/list/1', 7167)," +
                "('중앙도서관', '학술공지', 'https://library.sch.ac.kr/bbs/list/2', 7167), " +
                "('중앙도서관', '교육/행사공지', 'https://library.sch.ac.kr/bbs/list/3', 7167) " +
                "ON DUPLICATE KEY UPDATE sub_board_url = VALUES(sub_board_url)";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            int rowsAffected = stmt.executeUpdate(sql);

        } catch (SQLException e) {
            System.err.println("오류: 중앙도서관 게시판 정보 삽입 중 데이터베이스 오류가 발생했습니다.");
            e.printStackTrace();
        }
    }


    private int findBoardPageIdByTitle(String Title) throws SQLException {
        String sql = "SELECT id FROM crawl_pages WHERE UPPER(TRIM(title)) LIKE CONCAT(UPPER(TRIM(?)), '%')";        try (
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
        String sql = "SELECT id, title, absolute_url, category FROM " + tableName;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String title = rs.getString("title");
                String absolute_url = rs.getString("absolute_url");
                String category = rs.getString("category");
                boardPages.add(new BoardPage(title, null, absolute_url, category));
            }
        }
        return boardPages;
    }

    public List<BoardPage> loadSubBoardPagesFromDb(String tableName) throws SQLException {
        List<BoardPage> subBoards = new ArrayList<>();
        String sql = "SELECT B.title, S.board_name, S.sub_board_url AS absolute_url, B.category " +
                "FROM crawl_pages AS B JOIN " + tableName + " AS S ON B.id = S.page_id";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String title = rs.getString("title");
                String board_name = rs.getString("board_name");
                String absolute_url = rs.getString("absolute_url");
                String category = rs.getString("category");
                subBoards.add(new BoardPage(title, board_name, absolute_url, category));
            }
        }
        return subBoards;
    }

    public Set<String> loadExistingPostUrlsFromDb(String tableName) throws SQLException {
        Set<String> existingUrls = new HashSet<>();
        String sql = "SELECT external_source_url FROM " + tableName;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                existingUrls.add(rs.getString("external_source_url"));
            }
        }
        return existingUrls;
    }

    public void appendPostsToDb(List<BoardPost> newPosts) throws SQLException {
        // 1. SQL 쿼리를 상수로 정의하여 가독성을 높입니다.
        final String noticeSql = "INSERT INTO notice (title, content, created_at, view_count, category, notice_type) VALUES (?, ?, ?, ?, ?, 'CRAWL')";
        final String crawlPostsSql = "INSERT INTO crawl_posts (id, writer, external_source_url, source) VALUES (?, ?, ?, ?)";
        final String attachmentParentSql = "INSERT INTO attachment (file_name, file_url, attachment_type) VALUES (?, ?, 'CRAWL')";
        final String attachmentChildSql = "INSERT INTO crawl_attachment (id, crawl_posts_id) VALUES (?, ?)";

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement noticePstmt = conn.prepareStatement(noticeSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement crawlPostsPstmt = conn.prepareStatement(crawlPostsSql);
                 PreparedStatement attachmentParentPstmt = conn.prepareStatement(attachmentParentSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement attachmentChildPstmt = conn.prepareStatement(attachmentChildSql)) {

                for (BoardPost post : newPosts) {
                    if (post == null) continue;

                    noticePstmt.setString(1, post.getTitle());
                    noticePstmt.setString(2, post.getContent());
                    noticePstmt.setTimestamp(3, post.getpostDate());
                    noticePstmt.setInt(4, Integer.parseInt(post.getHits()));
                    noticePstmt.setString(5, post.getCategory());
                    noticePstmt.executeUpdate();

                    long noticeId;
                    try (ResultSet rs = noticePstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            noticeId = rs.getLong(1);
                        } else {
                            System.err.println("부모 notice의 ID를 가져오는데 실패했습니다: " + post.getTitle());
                            continue;
                        }
                    }

                    crawlPostsPstmt.setLong(1, noticeId);
                    crawlPostsPstmt.setString(2, post.getAuthor());
                    crawlPostsPstmt.setString(3, post.getAbsoluteUrl());
                    crawlPostsPstmt.setString(4, post.getDepartment());
                    crawlPostsPstmt.executeUpdate();

                    for (Attachment attachmentData : post.getAttachments()) {
                        attachmentParentPstmt.setString(1, attachmentData.getFileName());
                        attachmentParentPstmt.setString(2, attachmentData.getFileUrl());
                        attachmentParentPstmt.executeUpdate();

                        long attachmentId;
                        try (ResultSet rs = attachmentParentPstmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                attachmentId = rs.getLong(1);
                            } else {
                                System.err.println("부모 attachment의 ID를 가져오는데 실패했습니다: " + attachmentData.getFileName());
                                continue;
                            }
                        }

                        attachmentChildPstmt.setLong(1, attachmentId);
                        attachmentChildPstmt.setLong(2, noticeId);
                        attachmentChildPstmt.addBatch();
                    }
                }

                attachmentChildPstmt.executeBatch();

                conn.commit();

            } catch (SQLException e) {
                System.err.println("데이터베이스 작업 중 오류 발생. 트랜잭션을 롤백합니다.");
                if (conn != null) {
                    conn.rollback();
                }
                throw e;
            }

        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    public Set<String> loadExistingPostKeysFromDb(String postTableName) throws SQLException {
        Set<String> existingPostKeys = new HashSet<>();
        String sql = "SELECT external_source_url, source FROM " + postTableName;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String url = rs.getString("external_source_url");
                String department = rs.getString("source");
                if (url != null && department != null) {
                    existingPostKeys.add(url + "|" + department);
                }
            }
        }
        return existingPostKeys;
    }
}
