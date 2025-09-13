package com.whatisMGC;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import whatisMGC.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


public class WebCrawlerAppTest {
    public static void main(String[] args) throws IOException, URISyntaxException {
        HtmlFetcher htmlFetcher=new HtmlFetcher();
        PostInfo postInfo=new PostInfo();
        // Timestamp를 LocalDateTime으로 변환합니다.
        LocalDateTime localDateTime = Timestamp.from(Instant.now()).toLocalDateTime();

        // (1-1) LocalTime 객체로 시간 정보만 추출하기
        LocalTime localTime = localDateTime.toLocalTime();

        // (1-2) 원하는 형식의 시간 문자열로 추출하기 (HH:mm:ss)
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String formattedTime = localDateTime.format(timeFormatter);

//        // --- 테스트 설정 ---
//        // 테스트를 시작할 게시판의 URL을 여기에 입력하세요.
        String startUrl = "https://library.sch.ac.kr/bbs/list/1";
//        String startUrl = "https://library.sch.ac.kr/bbs/list/1";

//        // --------------------
//
//        System.out.println("'다음 페이지' 이동 및 '게시물 링크' 수집 테스트를 시작합니다.");
//        System.out.println("시작 URL: " + startUrl);
//        System.out.println("==============================================");
//
//        try {
//            // 시작 페이지의 HTML 문서를 가져옵니다.
//            Document currentDoc = htmlFetcher.getHTMLDocument(startUrl);
//            int pageCount = 1;
//
//            // '다음' 버튼이 없을 때까지 무한 반복합니다.
//            while (true) {
//                if (currentDoc == null) {
//                    System.out.println("오류: 페이지를 불러올 수 없습니다. 테스트를 중단합니다.");
//                    break;
//                }
//
//                // 현재 페이지 정보 출력
//                System.out.printf("[ %d 페이지 처리 중 ]\n", pageCount);
//                System.out.println("  - 현재 URL: " + currentDoc.location());
//                System.out.println("  - 페이지 제목: " + currentDoc.title());
//
//                // --- 현재 페이지의 모든 게시물 링크 가져오기 ---
//                System.out.println("  --- 이 페이지의 게시물 링크 목록 ---");
//                String postLinkSelector = "td.subject a, td> a[href*='article_no='],li a[href*='board_no='], .listTable tr td a, td.title a";
//                Elements postLinks = currentDoc.select(postLinkSelector);
//                Elements posts = currentDoc.select(".type_board  tbody  td, .listTable tbody  td");
//
//                if (postLinks.isEmpty()) {
//                    System.out.println("    >> 게시물 링크를 찾을 수 없습니다.");
//                } else {
//                    for (Element link : postLinks) {
//                        System.out.println("    - " + link.attr("abs:href"));
//                        for (Element post : posts) {
//                            System.out.println("title="+post.selectFirst(".board_title > :is(h1, h2, h3, h4, h5, h6), .type_board [class*=\"subject\"],td.title a").text());
//                            System.out.println("author="+post.selectFirst("ul > li:contains(작성자), [class*=\"writer\"], .type_board [class*=\"writer\"],td.writer").text());
//                            System.out.println("date="+post.selectFirst("ul > li:contains(등록일), [class*=\"postdate\"], .type_board [class*=\"date\"],td.reportDate").text());
//                            System.out.println("hits="+post.selectFirst("ul > li:contains(조회), .type_board [class*=\"hits\"],td.view_cnt").text());
//
//                        }
//
//                    }
//                }
//                System.out.println("  ------------------------------------");
//
//
//                // --- '다음 페이지' 이동 로직 ---
//                // 다양한 '다음' 버튼 형태에 대응하기 위한 CSS 선택자
//                String nextButtonSelector = "a.next.pager, .paging a[title*='다음'], .page_list a[title*='다음'], .pager a[title*='다음']";
//                Element nextElement = currentDoc.selectFirst(nextButtonSelector);
//
//                // '다음' 버튼 유무 확인
//                if (nextElement == null) {
//                    System.out.println("\n>> '다음' 버튼을 찾을 수 없습니다. 마지막 페이지입니다.");
//                    System.out.println("테스트를 종료합니다.");
//                    break;
//                }
//
//                String nextUrl = nextElement.attr("abs:href");
//                if (nextUrl.isEmpty() || !nextUrl.startsWith("http")) {
//                    System.out.println("\n>> '다음' 버튼의 URL이 유효하지 않습니다: " + nextUrl);
//                    System.out.println("테스트를 종료합니다.");
//                    break;
//                }
//
//                System.out.println("  - '다음' 버튼 링크 발견: " + nextUrl);
//                System.out.println("----------------------------------------------");
//
//                // 다음 페이지로 이동
//                currentDoc = htmlFetcher.getHTMLDocument(nextUrl);
//                pageCount++;
//            }
//
//        }catch (RuntimeException e) {
//            throw new RuntimeException(e);
//        }
        String postlink = "https://library.sch.ac.kr/bbs/list/1";

        String postdepartment = "";
        if (postlink.contains("library")){
            postdepartment = "library";
        }
        System.out.println(postdepartment);
        Document currentDoc = htmlFetcher.getHTMLDocument(startUrl);
        Elements posts = currentDoc.select(".type_board > tbody > tr,.listTable tbody  tr");
        String combinedSelector = "td.subject a, td> a[href*='article_no='],li a[href*='board_no='], .listTable tr td a, td.title a";


        for (Element post : posts) {
            Element linkElement = post.selectFirst(combinedSelector);
            String titleSelector=".board_title > :is(h1, h2, h3, h4, h5, h6), td.title a, type_board tbody [class*=\"subject\"], .subject";
            String authorSelector="ul > li:contains(작성자), [class*=\"writer\"],td.writer, .writer";
            String postDateSelector= "ul > li:contains(등록일), [class*=\"postdate\"], td.reportDate, .date";
            String posthitsSelector="ul > li:contains(조회), td.view_cnt, .hits";

//            String postlink = linkElement.attr("abs:href");
//            System.out.println(postlink);
//
//            String ttext=post.selectFirst(titleSelector).text().trim();
//            System.out.println(ttext);
//            String atext=post.selectFirst(authorSelector).text().trim();
//            System.out.println(atext);
//            String ptext=post.selectFirst(postDateSelector).text().trim();
//            System.out.println(ptext);
//            String htext=post.selectFirst(posthitsSelector).text().trim();
//            System.out.println(htext);
        }




    }
}
