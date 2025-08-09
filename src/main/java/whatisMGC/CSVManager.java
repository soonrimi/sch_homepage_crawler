package whatisMGC;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;


public class CSVManager {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd");
    private final String postsFilename;

    public CSVManager(String postsFilename) {
        this.postsFilename = postsFilename;
    }



    public static List<BoardPage> getPageList(String filename) throws IOException {
        List<BoardPage> boardPages = new ArrayList<>();
        File csvFile = new File(filename);

        if (!csvFile.exists()) {
            System.err.println("오류: CSV 파일이 존재하지 않습니다: " + filename);
            throw new IOException("CSV 파일이 존재하지 않습니다: " + filename);
        }
        if (csvFile.length() == 0) {
            System.err.println("오류: CSV 파일이 비어 있습니다: " + filename);
            throw new IOException("CSV 파일이 비어 있습니다: " + filename);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] lineArr = line.split(",", -1);
                for (int i = 0; i < lineArr.length; i++) {
                    if (lineArr[i] != null) {
                        lineArr[i] = lineArr[i].trim();
                        if (lineArr[i].startsWith("\"") && lineArr[i].endsWith("\"") && lineArr[i].length() > 1) {
                            lineArr[i] = lineArr[i].substring(1, lineArr[i].length() - 1).replace("\"\"", "\"");
                        }
                    }
                }

                if (lineArr.length >= 2 && !lineArr[0].trim().isEmpty() && !lineArr[1].trim().isEmpty()) {
                    try {
                        new java.net.URL(lineArr[1]).toURI(); // URL 유효성 검사
                        boardPages.add(new BoardPage(lineArr[0], lineArr[1], lineArr[2]));
                    } catch (MalformedURLException | URISyntaxException e) {
                        System.err.println("경고: 유효하지 않은 URL: " + lineArr[1]);
                    }
                } else {
                    System.err.println("경고: 잘못된 CSV 라인: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("CSV 파일 읽기 중 오류 발생: " + e.getMessage());
            throw e;
        }

        if (boardPages.isEmpty()) {
            System.err.println("오류: 유효한 페이지 데이터가 없습니다: " + filename);
        }

        return boardPages;
    }

    public Set<String> loadExistingPostUrlsFromCsv() {
        Set<String> existingUrls = new HashSet<>();
        File csvFile = new File(postsFilename);
        if (!csvFile.exists() || csvFile.length() == 0) {
            return existingUrls;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                // 빈 라인 건너뛰기
                if (line.trim().isEmpty()) {
                    continue;
                }

                // BOM 제거
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] parts = line.split(",", -1);

                // 따옴표 제거
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i] != null) {
                        parts[i] = parts[i].trim();
                        if (parts[i].startsWith("\"") && parts[i].endsWith("\"") && parts[i].length() > 1) {
                            parts[i] = parts[i].substring(1, parts[i].length() - 1);
                            parts[i] = parts[i].replace("\"\"", "\"");
                        }
                    }
                }

                if (parts.length > 4 && !parts[4].trim().isEmpty()) {
                    String url = parts[4];
                    existingUrls.add(url);
                }
            }
        } catch (IOException e) {
            System.err.println("기존 CSV 파일 로드 중 오류 발생: " + e.getMessage());
        }
        return existingUrls;
    }

    public void appendPostsToCsv(List<BoardPost> posts) {
        File csvFile = new File(postsFilename);
        boolean fileExists = csvFile.exists() && csvFile.length() > 0;

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile, true), StandardCharsets.UTF_8))) {
            // 파일이 새로 생성되거나 비어있으면 헤더를 작성
            Comparator<BoardPost> boardPostComparator = Comparator
                    .comparing(BoardPost::getPostTime, Comparator.reverseOrder()) // postTime 내림차순
                    .thenComparing(BoardPost::getDepartment) // department 오름차순
                    .thenComparing(BoardPost::getAbsoluteUrl); // absoluteUrl 오름차순

            Collections.sort(posts, boardPostComparator);
            if (!fileExists) {
                // 헤더 순서: department,title,author,postTime,absoluteUrl,content
                writer.write("department,title,author,postTime,absoluteUrl,content");
                writer.newLine();
            }

            for (BoardPost post : posts) {
                if (post == null) continue;

                String department = escapeCsvField(post.getDepartment());
                String title = escapeCsvField(post.getTitle());
                String author = escapeCsvField(post.getAuthor());
                String postTime = escapeCsvField(post.getPostTime());
                String absoluteUrl = escapeCsvField(post.getAbsoluteUrl());
                String content = escapeCsvField(post.getContent());

                // 헤더 순서에 맞춰 데이터 작성
                writer.write(String.join(",", department, title, author, postTime, absoluteUrl, content));
                writer.newLine();
            }
            System.out.println("데이터가 성공적으로 CSV 파일에 추가되었습니다: " + postsFilename);
        } catch (IOException e) {
            System.err.println("CSV 파일에 데이터 추가 중 오류 발생: " + e.getMessage());
        }
    }
//  get all pages at first run
//    public void saveBoardPagesToCsv(List<BoardPage> pages, String filename) {
//        File csvFile = new File(filename);
//
//        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile, false), StandardCharsets.UTF_8))) {
//            // 헤더 작성
//            writer.write("title,absoluteUrl, category");
//            writer.newLine();
//
//            // 데이터 작성
//            for (BoardPage page : pages) {
//                if (page == null) continue;
//
//                String title = escapeCsvField(page.getTitle());
//                String absoluteUrl = escapeCsvField(page.getAbsoluteUrl());
//
//                writer.write(String.join(",", title, absoluteUrl));
//                writer.newLine();
//            }
//            System.out.println("BoardPage 데이터가 성공적으로 CSV 파일에 저장되었습니다: " + filename);
//        } catch (IOException e) {
//            System.err.println("BoardPage 데이터를 CSV 파일에 저장 중 오류 발생: " + e.getMessage());
//        }
//    }
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}