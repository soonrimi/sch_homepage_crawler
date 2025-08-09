package com.whatisMGC;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import whatisMGC.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class WebCrawlerAppTest {
    public static void main(String[] args) throws IOException, URISyntaxException {
        //csv 파일 읽어와서 읽을 페이지 리스트 가져오기
        //1. 1일 주기로 가져오는거
        //2. https://home.sch.ac.kr/mediacomm/03/0101.jsp 여기서 department안따와짐
        //3. getallpages 완성해서 main에서는 주석처리, 설명
        List<BoardPage> testPages = Collections.singletonList(
                new BoardPage("미디어커뮤니케이션학과", "https://home.sch.ac.kr/mediacomm", "대학공지")
        );
        PostInfo crawler = new PostInfo();

        // 테스트 메서드 호출
        List<BoardPost> dailyPosts = crawler.getAllPosts(testPages);

        // 결과 출력 및 검증
        System.out.println("\n===== 테스트 결과 =====");
        System.out.println("총 발견된 오늘 게시글 수: " + dailyPosts.size());

        if (dailyPosts.size() == 2) {
            System.out.println("테스트 통과: 오늘 글 2개가 정확하게 발견되었습니다.");
            dailyPosts.forEach(System.out::println);
        } else {
            System.out.println("테스트 실패: 예상치 못한 결과입니다. (발견된 글 수: " + dailyPosts.size() + ")");
            dailyPosts.forEach(System.out::println);
        }


    }
}
