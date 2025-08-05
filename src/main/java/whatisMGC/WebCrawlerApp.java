package whatisMGC;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;


public class WebCrawlerApp {
    private static final String LINKS_BY_CLASS_CSV = "./src/main/resources/links_by_class.csv";
    private static final String POSTS_CSV = "./src/main/resources/posts.csv";

    public static void main(String[] args) throws IOException, URISyntaxException {

        CSVManager csvManager = new CSVManager(POSTS_CSV);
        PostInfo postInfo = new PostInfo();


//  get all pages at first run
//
//        HtmlFetcher htmlFetcher = new HtmlFetcher();
//        BoardInfo boardInfo = new BoardInfo();
//        Document doc=htmlFetcher.getHTMLDocument("https://home.sch.ac.kr/sch/index.jsp");
//        List<BoardPage> allPages=boardInfo.getAllPages(doc);
//        csvManager.saveBoardPagesToCsv(allPages, "links_by_class.csv");
        //csv 파일 읽어와서 읽을 페이지 리스트 가져오기
        List<BoardPage> pageList= CSVManager.getPageList(LINKS_BY_CLASS_CSV);
        if (pageList.isEmpty()) {
            System.err.println("오류: 크롤링할 페이지 리스트가 비어 있습니다. links_by_class.csv 파일을 확인하세요.");
            return;
        }
//        List<BoardPost> dailyPosts=postInfo.getDailyPosts(pageList);

        //페이지 리스트를 이용해서 post들 크롤링
        List<BoardPost> allPosts = postInfo.getAllPosts(pageList);
        Set<String> existingUrls = csvManager.loadExistingPostUrlsFromCsv();
        // csv에 링크 등록되어있는 게시물 제외
        List<BoardPost> newPostsToSave = allPosts.stream()
                .filter(post -> !existingUrls.contains(post.getAbsoluteUrl()))
                .toList();
        if (!newPostsToSave.isEmpty()) {
            csvManager.appendPostsToCsv(newPostsToSave);
        } else {
            System.out.println("새로 추가된 게시물이 없습니다.");
        }

    }
}




