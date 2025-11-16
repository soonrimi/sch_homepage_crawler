package whatisMGC;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import com.google.gson.Gson;

public class DBManager {
    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASS;
    private static final Gson gson = new Gson();
    static {
        DB_URL = DBConfig.DB_URL;
        DB_USER = DBConfig.DB_USER;
        DB_PASS = DBConfig.DB_PASSWORD;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    public void createAllTablesSequentially() {
        System.out.println("데이터베이스 테이블 스키마 검사 및 생성을 시작합니다...");
        try (Connection conn = getConnection()) {

            // 1. crawl_pages
            executeCreateTable(conn, "crawl_pages", getCrawlPagesDdl());
            addMissingColumn(conn, "crawl_pages", "id", "INT(11) NOT NULL AUTO_INCREMENT PRIMARY KEY");
            addMissingColumn(conn, "crawl_pages", "absolute_url", "VARCHAR(512) NOT NULL");
            addMissingColumn(conn, "crawl_pages", "category", "VARCHAR(100) DEFAULT NULL");
            addMissingColumn(conn, "crawl_pages", "title", "VARCHAR(255) NOT NULL");
            addMissingIndex(conn, "crawl_pages", "uk_crawl_pages_absolute_url", "UNIQUE (absolute_url)");

            // --- 2. notice  ---
            executeCreateTable(conn, "notice", getNoticeDdl());
            addMissingColumn(conn, "notice", "id", "BIGINT(20) NOT NULL AUTO_INCREMENT PRIMARY KEY");
            addMissingColumn(conn, "notice", "notice_type", "VARCHAR(31) NOT NULL");
            addMissingColumn(conn, "notice", "category", "ENUM('ACTIVITY','DEPARTMENT','GRADE','PROMOTION','RECRUIT','UNIVERSITY') NOT NULL");
            addMissingColumn(conn, "notice", "content", "LONGTEXT NOT NULL");
            addMissingColumn(conn, "notice", "created_at", "TIMESTAMP NOT NULL");
            addMissingColumn(conn, "notice", "title", "VARCHAR(255) NOT NULL");
            addMissingColumn(conn, "notice", "view_count", "INT(11) DEFAULT NULL");
            addMissingColumn(conn, "notice", "content_hash", "VARCHAR(64) DEFAULT NULL");


            // --- 3. attachment (변경 없음) ---
            executeCreateTable(conn, "attachment", getAttachmentDdl());
            addMissingColumn(conn, "attachment", "id", "BIGINT(20) NOT NULL AUTO_INCREMENT PRIMARY KEY");
            addMissingColumn(conn, "attachment", "attachment_type", "VARCHAR(31) NOT NULL"); // Discriminator
            addMissingColumn(conn, "attachment", "file_name", "VARCHAR(255) NOT NULL");
            addMissingColumn(conn, "attachment", "file_url", "VARCHAR(255) NOT NULL");
            addMissingColumn(conn, "attachment", "notice_id", "BIGINT(20) DEFAULT NULL");
            addMissingIndex(conn, "attachment", "fk_attachment_notice", "(notice_id)");
            addMissingIndex(conn, "attachment", "fk_attachment_notice", "FOREIGN KEY (notice_id) REFERENCES notice (id) ON DELETE CASCADE");

            // --- 4. sub_crawl_pages (변경 없음) ---
            executeCreateTable(conn, "sub_crawl_pages", getSubCrawlPagesDdl());
            addMissingColumn(conn, "sub_crawl_pages", "id", "INT(11) NOT NULL AUTO_INCREMENT PRIMARY KEY");
            addMissingColumn(conn, "sub_crawl_pages", "parent_page_title", "VARCHAR(255) NOT NULL");
            addMissingColumn(conn, "sub_crawl_pages", "board_name", "VARCHAR(255) NOT NULL");
            addMissingColumn(conn, "sub_crawl_pages", "sub_board_url", "VARCHAR(2048) NOT NULL");
            addMissingColumn(conn, "sub_crawl_pages", "page_id", "INT(11) NOT NULL");
            addMissingIndex(conn, "sub_crawl_pages", "uk_sub_crawl_pages_sub_board_url", "UNIQUE (sub_board_url)");
            addMissingIndex(conn, "sub_crawl_pages", "fk_sub_crawl_pages_to_crawl_pages", "(page_id)");
            addMissingIndex(conn, "sub_crawl_pages", "fk_sub_crawl_pages_to_crawl_pages", "FOREIGN KEY (page_id) REFERENCES crawl_pages(id) ON DELETE CASCADE ON UPDATE CASCADE");

            // --- 5. crawl_posts  ---
            executeCreateTable(conn, "crawl_posts", getCrawlPostsDdl());
            addMissingColumn(conn, "crawl_posts", "id", "BIGINT(20) NOT NULL PRIMARY KEY");
            addMissingColumn(conn, "crawl_posts", "external_source_url", "VARCHAR(255) DEFAULT NULL");
            addMissingColumn(conn, "crawl_posts", "content_images", "JSON DEFAULT NULL");
            addMissingColumn(conn, "crawl_posts", "source", "VARCHAR(255) DEFAULT NULL");
            addMissingColumn(conn, "crawl_posts", "writer", "VARCHAR(255) DEFAULT NULL");
            addMissingIndex(conn, "crawl_posts", "uk_crawl_posts_external_source_url", "UNIQUE (external_source_url)");
            addMissingIndex(conn, "crawl_posts", "crawl_posts_notice_FK", "FOREIGN KEY (id) REFERENCES notice(id) ON DELETE CASCADE ON UPDATE CASCADE");


            System.out.println("테이블 스키마 검사 및 생성 작업이 성공적으로 완료되었습니다.");
        } catch (SQLException e) {
            System.err.println("테이블 생성/검증 과정 중 데이터베이스 연결 오류가 발생했습니다.");
            e.printStackTrace();
        }
    }

    private void executeCreateTable(Connection conn, String tableName, String ddl) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
            System.out.println("- '" + tableName + "' 테이블이 준비되었습니다.");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1050) {
                System.err.println("오류: '" + tableName + "' 테이블 생성 실패 (ErrorCode: " + e.getErrorCode() + ")");
                e.printStackTrace();
            }
        }
    }
    private void addMissingColumn(Connection conn, String tableName, String columnName, String columnDefinition) {
        try {
            String checkColumnSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = ? " +
                    "AND COLUMN_NAME = ?";
            boolean columnExists = false;
            try (PreparedStatement pstmt = conn.prepareStatement(checkColumnSql)) {
                pstmt.setString(1, tableName);
                pstmt.setString(2, columnName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        columnExists = true;
                    }
                }
            }
            if (!columnExists) {
                String alterTableSql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(alterTableSql);
                    System.out.println("  -> '" + tableName + "' 테이블에 '" + columnName + "' 컬럼 추가.");
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("경고: '" + tableName + "." + columnName + "' 컬럼 추가 시도 중 오류: " + e.getMessage());
            }
        }
    }
    private void addMissingIndex(Connection conn, String tableName, String indexName, String indexDefinition) {
        try {
            String checkIndexSql = "SHOW INDEX FROM " + tableName + " WHERE Key_name = ?";
            boolean indexExists = false;
            try (PreparedStatement pstmt = conn.prepareStatement(checkIndexSql)) {
                pstmt.setString(1, indexName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        indexExists = true;
                    }
                }
            }
            if (!indexExists) {
                String alterTableSql;
                String upperDef = indexDefinition.toUpperCase();
                if (upperDef.startsWith("UNIQUE") || upperDef.startsWith("PRIMARY KEY")) {
                    alterTableSql = "ALTER TABLE " + tableName + " ADD CONSTRAINT " + indexName + " " + indexDefinition;
                } else if (upperDef.startsWith("FOREIGN KEY")) {
                    alterTableSql = "ALTER TABLE " + tableName + " ADD CONSTRAINT " + indexName + " " + indexDefinition;
                } else {
                    alterTableSql = "ALTER TABLE " + tableName + " ADD INDEX " + indexName + " " + indexDefinition;
                }
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(alterTableSql);
                    System.out.println("  -> '" + tableName + "' 테이블에 '" + indexName + "' 제약조건/인덱스 추가.");
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() != 1061 && e.getErrorCode() != 1072 && e.getErrorCode() != 1826) {
                System.err.println("경고: '" + tableName + "." + indexName + "' 제약조건 추가 시도 중 오류 (무시): " + e.getMessage());
            }
        }
    }

    // --- (DDL 헬퍼 메소드들) ---
    private String getCrawlPagesDdl() {
        return "CREATE TABLE IF NOT EXISTS crawl_pages (" +
                "    id INT(11) NOT NULL AUTO_INCREMENT, " +
                "    absolute_url VARCHAR(512) NOT NULL, " +
                "    category VARCHAR(100) DEFAULT NULL, " +
                "    title VARCHAR(255) NOT NULL, " +
                "    PRIMARY KEY (id), " +
                "    UNIQUE KEY uk_crawl_pages_absolute_url (absolute_url)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
    }


    private String getNoticeDdl() {
        return "CREATE TABLE IF NOT EXISTS notice (" +
                "    id BIGINT(20) NOT NULL AUTO_INCREMENT, " +
                "    notice_type VARCHAR(31) NOT NULL, " +
                "    category ENUM('ACTIVITY','DEPARTMENT','GRADE','PROMOTION','RECRUIT','UNIVERSITY') NOT NULL, " +
                "    content LONGTEXT NOT NULL, " +
                "    created_at TIMESTAMP NOT NULL, " +
                "    title VARCHAR(255) NOT NULL, " +
                "    view_count INT(11) DEFAULT NULL, " +
                "    content_hash VARCHAR(64) DEFAULT NULL, " +
                "    PRIMARY KEY (id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
    }
    private String getAttachmentDdl() {
        return "CREATE TABLE IF NOT EXISTS attachment (" +
                "    id BIGINT(20) NOT NULL AUTO_INCREMENT, " +
                "    attachment_type VARCHAR(31) NOT NULL, " +
                "    file_name VARCHAR(255) NOT NULL, " +
                "    file_url VARCHAR(255) NOT NULL, " +
                "    notice_id BIGINT(20) DEFAULT NULL, " +
                "    PRIMARY KEY (id), " +
                "    KEY fk_attachment_notice (notice_id), " +
                "    CONSTRAINT fk_attachment_notice FOREIGN KEY (notice_id) REFERENCES notice (id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
    }

    private String getSubCrawlPagesDdl() {
        return "CREATE TABLE IF NOT EXISTS sub_crawl_pages (" +
                "    id INT(11) NOT NULL AUTO_INCREMENT, " +
                "    parent_page_title VARCHAR(255) NOT NULL, " +
                "    board_name VARCHAR(255) NOT NULL, " +
                "    sub_board_url VARCHAR(2048) NOT NULL, " +
                "    page_id INT(11) NOT NULL, " +
                "    PRIMARY KEY (id), " +
                "    UNIQUE KEY uk_sub_crawl_pages_sub_board_url (sub_board_url), " +
                "    KEY fk_sub_crawl_pages_to_crawl_pages (page_id), " +
                "    CONSTRAINT fk_sub_crawl_pages_to_crawl_pages FOREIGN KEY (page_id) REFERENCES crawl_pages (id) ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
    }

    private String getCrawlPostsDdl() {
        return "CREATE TABLE IF NOT EXISTS crawl_posts (" +
                "    id BIGINT(20) NOT NULL, " +
                "    external_source_url VARCHAR(255) DEFAULT NULL, " +
                "    source VARCHAR(255) DEFAULT NULL, " +
                "    writer VARCHAR(255) DEFAULT NULL, " +
                "    content_images LONGTEXT DEFAULT NULL, " +
                "    PRIMARY KEY (id), " +
                "    UNIQUE KEY uk_crawl_posts_external_source_url (external_source_url), " +
                "    CONSTRAINT crawl_posts_notice_FK FOREIGN KEY (id) REFERENCES notice (id) ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
    }


    // --- (게시판 저장 관련 메소드 ) ---
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
    public void saveSubBoards(List<BoardPage> subBoards, String tableName, BoardPage parentPage) throws SQLException {
        String sql = "INSERT INTO " + tableName + " (parent_page_title, board_name, sub_board_url, page_id) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE parent_page_title = VALUES(parent_page_title), board_name = VALUES(board_name), page_id = VALUES(page_id)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            int parentId = findBoardPageIdByTitle(parentPage.getTitle());
            if (parentId == -1) {
                System.err.printf("경고: URL '%s' (Title: %s)에 해당하는 부모 게시판 ID를 찾을 수 없어 소게시판을 저장하지 못했습니다.\n", parentPage.getAbsoluteUrl(), parentPage.getTitle());
                return;
            }
            for (BoardPage subBoard : subBoards) {
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
    public void insertHardcodedLibraryBoards(String tableName) {
        try {
            int libraryPageId = findBoardPageIdByTitle("중앙도서관");
            if (libraryPageId == -1) {
                System.err.println("오류: '중앙도서관'의 page_id를 찾을 수 없어 하드코딩된 게시판을 삽입할 수 없습니다.");
                return;
            }
            String sql = "INSERT INTO " + tableName + " (parent_page_title, board_name, sub_board_url, page_id) VALUES " +
                    "('중앙도서관', '일반공지', 'https://library.sch.ac.kr/bbs/list/1', ?)," +
                    "('중앙도서관', '학술공지', 'https://library.sch.ac.kr/bbs/list/2', ?), " +
                    "('중앙도서관', '교육/행사공지', 'https://library.sch.ac.kr/bbs/list/3', ?) " +
                    "ON DUPLICATE KEY UPDATE parent_page_title = VALUES(parent_page_title), board_name = VALUES(board_name), page_id = VALUES(page_id)";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, libraryPageId);
                pstmt.setInt(2, libraryPageId);
                pstmt.setInt(3, libraryPageId);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("오류: 중앙도서관 게시판 정보 삽입 중 데이터베이스 오류가 발생했습니다.");
            e.printStackTrace();
        }
    }
    private int findBoardPageIdByTitle(String Title) throws SQLException {
        String sql = "SELECT id FROM crawl_pages WHERE TRIM(title) = TRIM(?)";
        try (
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

    // --- 게시물 저장/업데이트 로직 ---

    private String calculateContentHash(String content) {
        return calculateContentHash(content, null);
    }
    public String calculateContentHash(String content, List<String> imageUrls) {
        if (content == null) {
            content = "";
        }

        String imageUrlsString = "";
        if (imageUrls != null && !imageUrls.isEmpty()) {
            List<String> sortedImageUrls = new ArrayList<>(imageUrls);
            Collections.sort(sortedImageUrls);
            imageUrlsString = String.join("||", sortedImageUrls);
        }

        String combinedContent = content + "||IMAGES:" + imageUrlsString;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(combinedContent.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            System.err.println("해시 계산 중 오류 발생: " + e.getMessage());
            return null;
        }
    }


    /**
     * 트랜잭션 관리를 상위 메서드(saveOrUpdatePostsInBulk)로 위임
     */
    private void insertNewPost(Connection conn, BoardPost post, String newHash) throws SQLException {
        final String noticeSql = "INSERT INTO notice (notice_type, category, content, created_at, title, view_count, content_hash) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        final String crawlPostsSql = "INSERT INTO crawl_posts (external_source_url, source, writer, id, content_images) " +
                "VALUES (?, ?, ?, ?, ?)";

        final String attachmentSql = "INSERT INTO attachment (attachment_type, file_name, file_url, notice_id) " +
                "VALUES (?, ?, ?, ?)";

        long noticeId;

        // 1. notice 테이블 삽입
        try (PreparedStatement noticePstmt = conn.prepareStatement(noticeSql, Statement.RETURN_GENERATED_KEYS)) {
            String title = post.getTitle();
            if (title != null && title.length() > 255) {
                title = title.substring(0, 255);
            }
            String contentValue = post.getContent() != null ? post.getContent() : "";
            Timestamp createdAtValue = post.getpostDate();
            if (createdAtValue == null) {
                throw new SQLException("PostInfo가 날짜 파싱에 실패 (created_at is null) Post: " + title);
            }
            int viewCountValue = post.getHits();
            String categoryValue = post.getCategory();
            String noticeType = "CRAWL";

            noticePstmt.setString(1, noticeType);
            noticePstmt.setString(2, categoryValue);
            noticePstmt.setString(3, contentValue);
            noticePstmt.setTimestamp(4, createdAtValue);
            noticePstmt.setString(5, title);
            noticePstmt.setInt(6, viewCountValue);
            noticePstmt.setString(7, newHash);

            noticePstmt.executeUpdate();

            try (ResultSet rs = noticePstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    noticeId = rs.getLong(1);
                } else {
                    throw new SQLException("notice ID 생성 실패: " + post.getTitle());
                }
            }
        }

        // 2. crawl_posts 삽입
        try (PreparedStatement crawlPostsPstmt = conn.prepareStatement(crawlPostsSql)) {
            String externalUrl = post.getAbsoluteUrl();
            if (externalUrl != null && externalUrl.length() > 2048) {
                externalUrl = externalUrl.substring(0, 2048);
            }

            crawlPostsPstmt.setString(1, externalUrl);
            crawlPostsPstmt.setString(2, post.getDepartment());
            crawlPostsPstmt.setString(3, post.getAuthor());
            crawlPostsPstmt.setLong(4, noticeId);
            String imageUrlsJson = gson.toJson(post.getContentImageUrls());
            crawlPostsPstmt.setString(5, imageUrlsJson);

            crawlPostsPstmt.executeUpdate();
        }

        if (post.getAttachments() != null && !post.getAttachments().isEmpty()) {
            try (PreparedStatement attachmentPstmt = conn.prepareStatement(attachmentSql)) {
                for (Attachment attachmentData : post.getAttachments()) {
                    String fileUrl = attachmentData.getFileUrl();
                    if (fileUrl != null && fileUrl.length() > 2048) {
                        fileUrl = fileUrl.substring(0, 2048);
                    }

                    attachmentPstmt.setString(1, "CRAWL");
                    attachmentPstmt.setString(2, attachmentData.getFileName());
                    attachmentPstmt.setString(3, fileUrl);
                    attachmentPstmt.setLong(4, noticeId);
                    attachmentPstmt.addBatch();
                }
                attachmentPstmt.executeBatch();
            }
        }
    }

    // 첨부파일만 별도로 삽입하는 헬퍼 메서드 (수정됨)
    private void insertAttachments(Connection conn, long noticeId, List<Attachment> attachments) throws SQLException {
        final String attachmentSql = "INSERT INTO attachment (attachment_type, file_name, file_url, notice_id) " +
                "VALUES (?, ?, ?, ?)";

        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        try (PreparedStatement attachmentPstmt = conn.prepareStatement(attachmentSql)) {
            for (Attachment attachmentData : attachments) {
                String fileUrl = attachmentData.getFileUrl();
                if (fileUrl != null && fileUrl.length() > 2048) {
                    fileUrl = fileUrl.substring(0, 2048);
                }

                attachmentPstmt.setString(1, "CRAWL");
                attachmentPstmt.setString(2, attachmentData.getFileName());
                attachmentPstmt.setString(3, fileUrl);
                attachmentPstmt.setLong(4, noticeId);
                attachmentPstmt.addBatch();
            }
            attachmentPstmt.executeBatch();
        }
    }

    private void updateExistingPost(Connection conn, long existingNoticeId, BoardPost post, String newHash) throws SQLException {
        final String noticeUpdateSql = "UPDATE notice SET notice_type = ?, category = ?, content = ?, created_at = ?, title = ?, view_count = ?, content_hash = ? WHERE id = ?";

        final String crawlPostUpdateSql = "UPDATE crawl_posts SET external_source_url = ?, source = ?, writer = ?, content_images = ? WHERE id = ?";

        final String findOldAttachmentsSql = "SELECT id, file_url FROM attachment WHERE notice_id = ?";

        final String deleteAttachmentSql = "DELETE FROM attachment WHERE id IN (?)";

        try (PreparedStatement noticeUpdatePstmt = conn.prepareStatement(noticeUpdateSql)) {
            String title = post.getTitle();
            if (title != null && title.length() > 255) {
                title = title.substring(0, 255);
            }
            String contentValue = post.getContent() != null ? post.getContent() : "";
            Timestamp createdAtValue = post.getpostDate();
            if (createdAtValue == null) {
                throw new SQLException("PostInfo가 날짜 파싱에 실패 (created_at is null) Post: " + title);
            }
            int viewCountValue = post.getHits();
            String categoryValue = post.getCategory();
            String noticeType = "CRAWL";

            noticeUpdatePstmt.setString(1, noticeType);
            noticeUpdatePstmt.setString(2, categoryValue);
            noticeUpdatePstmt.setString(3, contentValue);
            noticeUpdatePstmt.setTimestamp(4, createdAtValue);
            noticeUpdatePstmt.setString(5, title);
            noticeUpdatePstmt.setInt(6, viewCountValue);
            noticeUpdatePstmt.setString(7, newHash);
            noticeUpdatePstmt.setLong(8, existingNoticeId);

            noticeUpdatePstmt.executeUpdate();
        }

        try (PreparedStatement crawlPostUpdatePstmt = conn.prepareStatement(crawlPostUpdateSql)) {
            String externalUrl = post.getAbsoluteUrl();
            if (externalUrl != null && externalUrl.length() > 2048) {
                externalUrl = externalUrl.substring(0, 2048);
            }

            crawlPostUpdatePstmt.setString(1, externalUrl);
            crawlPostUpdatePstmt.setString(2, post.getDepartment());
            crawlPostUpdatePstmt.setString(3, post.getAuthor());
            String imageUrlsJson = gson.toJson(post.getContentImageUrls());
            crawlPostUpdatePstmt.setString(4, imageUrlsJson);
            crawlPostUpdatePstmt.setLong(5, existingNoticeId);

            crawlPostUpdatePstmt.executeUpdate();
        }

        Map<String, Long> oldUrlToIdMap = new HashMap<>();
        try(PreparedStatement findPstmt = conn.prepareStatement(findOldAttachmentsSql)) {
            findPstmt.setLong(1, existingNoticeId);
            try(ResultSet rs = findPstmt.executeQuery()) {
                while(rs.next()) {
                    oldUrlToIdMap.put(rs.getString("file_url"), rs.getLong("id"));
                }
            }
        }

        List<Attachment> newAttachments = post.getAttachments() != null ? post.getAttachments() : Collections.emptyList();
        List<Attachment> attachmentsToAdd = new ArrayList<>();

        for (Attachment newAtt : newAttachments) {
            String newFileUrl = newAtt.getFileUrl();
            if (newFileUrl != null && newFileUrl.length() > 2048) {
                newFileUrl = newFileUrl.substring(0, 2048);
            }

            if (oldUrlToIdMap.containsKey(newFileUrl)) {
                oldUrlToIdMap.remove(newFileUrl);
            } else {
                attachmentsToAdd.add(newAtt);
            }
        }

        List<Long> idsToDelete = new ArrayList<>(oldUrlToIdMap.values());
        if (!idsToDelete.isEmpty()) {
            String idPlaceholders = idsToDelete.stream().map(String::valueOf).collect(Collectors.joining(","));
            String finalDeleteSql = "DELETE FROM attachment WHERE id IN (" + idPlaceholders + ")";
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(finalDeleteSql);
            }
        }

        insertAttachments(conn, existingNoticeId, attachmentsToAdd);
    }


    private static class ExistingPostData {
        final long id;
        final String contentHash;
        final String dbTitle;

        ExistingPostData(long id, String contentHash, String dbTitle) {
            this.id = id;
            this.contentHash = contentHash;
            this.dbTitle = dbTitle;
        }
    }

    public void saveOrUpdatePostsInBulk(List<BoardPost> newPosts) {
        if (newPosts == null || newPosts.isEmpty()) {
            System.out.println("처리할 게시물이 없습니다.");
            return;
        }

        // --- 1. 데이터 준비 (메모리) ---
        Map<String, BoardPost> postsToProcess = new HashMap<>(); // key: 복합 키
        Map<BoardPost, String> postHashMap = new HashMap<>(); // key: BoardPost
        Set<String> urlsToCheck = new HashSet<>();

        System.out.println("1. 크롤링된 게시물 " + newPosts.size() + "건 해시 계산 및 키 생성 시작...");
        for (BoardPost post : newPosts) {
            //  title만 null 체크
            if (post == null || post.getTitle() == null || post.getAbsoluteUrl() == null) continue;
            // if (post == null || post.getTitle() == null || post.getAuthor() == null || post.getCategory() == null) continue; //

            String newHash = post.getContentHash();
            if (newHash == null) {
                newHash = calculateContentHash(post.getContent(), post.getContentImageUrls());
            }

            if (newHash == null) {
                System.err.println("해시 계산 실패로 건너뜀: " + post.getTitle());
                continue;
            }

            if (post.getTitle().length() > 255) {
                System.err.println("제목 길이가 255자를 초과하여 건너뜁니다: " + post.getTitle().substring(0, 100) + "...");
                continue;
            }
            // if (post.getAuthor().length() > 255) {
            //  sout.println("작성자 길이가 255자를 초과하여 건너뜁니다: " + post.getAuthor().substring(0, 100) + "...");
            //  continue;
            // } // [주석]

            // [수정] 키를 title로만 사용
            String urlKey = post.getAbsoluteUrl();
            // String compositeKey = post.getTitle() + "||" + post.getAuthor() + "||" + post.getCategory(); //

            if (!postsToProcess.containsKey(urlKey)) {
                postsToProcess.put(urlKey, post);
                postHashMap.put(post, newHash);
                urlsToCheck.add(urlKey);
            }
        }

        if (postsToProcess.isEmpty()) {
            System.out.println("유효한 게시물이 없습니다.");
            return;
        }
        System.out.println(" - 유효한 키 " + postsToProcess.size() + "건 확인."); // [수정] "복합 키" -> "키"

        // --- 2. 기존 데이터 한번에 조회 (DB 쿼리 딱 1번) ---
        Map<String, ExistingPostData> existingPostsMap = new HashMap<>(); // <CompositeKey, ExistingPostData(id, hash)>

        String findSql = "SELECT n.id, n.title, n.content_hash, cp.external_source_url " +
                "FROM notice n " +
                "JOIN crawl_posts cp ON n.id = cp.id " +
                "WHERE cp.external_source_url IN (" + createPlaceholders(urlsToCheck.size()) + ")";

        Connection conn = null;
        boolean success = false;

        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            System.out.println("2. DB에서 기존 게시물 후보 정보 조회 시작...");
            try (PreparedStatement findPstmt = conn.prepareStatement(findSql)) {
                int i = 1;
                for (String url : urlsToCheck) {
                    findPstmt.setString(i++, url);
                }
                try (ResultSet rs = findPstmt.executeQuery()) {
                    while (rs.next()) {
                        String dbUrl = rs.getString("external_source_url");
                        existingPostsMap.put(
                                dbUrl,
                                new ExistingPostData(
                                        rs.getLong("id"),
                                        rs.getString("content_hash"),
                                        rs.getString("title") // DB에 저장된 제목 가져오기
                                )
                        );
                    }
                }
            }
            System.out.println(" - 기존 게시물 " + existingPostsMap.size() + "건 정보 로드 완료.");


            // --- 3. 비교 및 분류 ---
            List<BoardPost> postsToInsert = new ArrayList<>();
            List<Map.Entry<Long, BoardPost>> postsToUpdate = new ArrayList<>();

            for (Map.Entry<String, BoardPost> entry : postsToProcess.entrySet()) {
                String urlKey = entry.getKey(); // URL
                BoardPost post = entry.getValue();
                String newHash = postHashMap.get(post);

                if (existingPostsMap.containsKey(urlKey)) {
                    // URL이 DB에 존재함 -> 기존 글임
                    ExistingPostData existingData = existingPostsMap.get(urlKey);

                    // [판단 로직] 내용(해시)이 다르거나 OR 제목이 다르면 -> 업데이트 대상
                    boolean isContentChanged = !newHash.equals(existingData.contentHash);
                    boolean isTitleChanged = !post.getTitle().equals(existingData.dbTitle);

                    if (isContentChanged || isTitleChanged) {
                        postsToUpdate.add(Map.entry(existingData.id, post));
                    }
                } else {
                    // URL이 DB에 없음 -> 진짜 새 글임
                    postsToInsert.add(post);
                }
            }
            System.out.println(" - 신규: " + postsToInsert.size() + "건, 변경: " + postsToUpdate.size() + "건 분류 완료.");

            // --- 4. DB 작업 실행 (헬퍼 메소드 호출) ---
            System.out.println("4. DB 작업 실행 (신규 " + postsToInsert.size() + "건, 변경 " + postsToUpdate.size() + "건)...");

            int insertSuccess = 0;
            for (BoardPost post : postsToInsert) {
                Savepoint sp = null;
                try {
                    sp = conn.setSavepoint();
                    String newHash = postHashMap.get(post);
                    insertNewPost(conn, post, newHash);
                    insertSuccess++;
                } catch (SQLException e) {
                    if (sp != null) try { conn.rollback(sp); } catch (SQLException rb) {}
                    System.err.println("INSERT 실패 (건너뜀): " + post.getTitle() + " [" + e.getMessage() + "]");
                }
            }

            int updateSuccess = 0;
            for (Map.Entry<Long, BoardPost> entry : postsToUpdate) {
                Savepoint sp = null;
                try {
                    sp = conn.setSavepoint();
                    Long existingId = entry.getKey();
                    BoardPost post = entry.getValue();
                    String newHash = postHashMap.get(post);
                    updateExistingPost(conn, existingId, post, newHash);
                    updateSuccess++;
                } catch (SQLException e) {
                    if (sp != null) try { conn.rollback(sp); } catch (SQLException rb) {}
                    System.err.println("UPDATE 실패 (건너뜀): " + entry.getValue().getTitle() + " [" + e.getMessage() + "]");
                }
            }

            conn.commit();
            success = true;
            System.out.printf("DB 작업 완료. (신규: %d, 변경: %d)\n", insertSuccess, updateSuccess);

        } catch (SQLException e) {
            System.err.println("!!! BULK 처리 중 오류 발생. 전체 트랜잭션을 롤백합니다. !!!");
            e.printStackTrace();
            if (conn != null) {
                try {
                    System.err.println("롤백을 시도합니다...");
                    conn.rollback();
                    System.err.println("롤백 완료.");
                } catch (SQLException rollbackEx) {
                    System.err.println("롤백 중 추가 오류 발생: " + rollbackEx.getMessage());
                }
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException finalEx) {
                    System.err.println("DB 리소스 정리 중 오류: " + finalEx.getMessage());
                }
            }
        }
    }

    /**
     * PreparedStatement의 IN 절을 위한 ? 플레이스홀더 문자열 생성 헬퍼
     */
    private String createPlaceholders(int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("?");
            if (i < count - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }
}


