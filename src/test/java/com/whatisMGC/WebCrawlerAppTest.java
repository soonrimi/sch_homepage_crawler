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
        HtmlFetcher htmlFetcher = new HtmlFetcher();
//        List<String> pageList = Arrays.asList(
//                "https://homepage.sch.ac.kr/src/05/01.jsp",
//                "https://homepage.sch.ac.kr/gyomu/06/03.jsp",
//                "https://library.sch.ac.kr/bbs/list/1",
////                "http://hrc.sch.ac.kr/notificationList.php",
//                "https://homepage.sch.ac.kr/edu/06/03.jsp",
//                "https://home.sch.ac.kr/pharmen/06/0201.jsp",
//                "https://home.sch.ac.kr/medibio/06/0201.jsp",
//                "https://home.sch.ac.kr/law/05/01.jsp"
//        );
//        for (String page:pageList){
//            Document doc=htmlFetcher.getHTMLDocument(page);
//            String combinedSelector=("a.pager, a.pager.next, a.pager.last, a[title*='마지막으로'], a[title*='다음으로'],a[title*='다음'],a[title*='맨뒤'],a[title*='pagego']");
////        System.out.println(doc);
//            System.out.println(doc.selectFirst(combinedSelector).attr("abs:href"));
//        }

        String page=("https://library.sch.ac.kr/bbs/list/1");
        Document doc=htmlFetcher.getHTMLDocument(page);
        System.out.println(doc.selectFirst(".listTable tr td a").attr("abs:href"));




    }
}
