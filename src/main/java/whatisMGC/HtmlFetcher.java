package whatisMGC;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

public class HtmlFetcher {
    //웹브라우저의 USER-AGENT 설정
    private static final String CHROME_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    //url을 받아 접속, 해당 url의 html 문서 가져오기
    public Document getHTMLDocument(String url) {
        try{
            Connection.Response response =  Jsoup.connect(url)
                    .userAgent(CHROME_USER_AGENT) //사람처럼 행동하는 브라우저로 가장
                    .header("Accept-Language", "ko-KR,ko;q=0.9") // 한국어 페이지 우선 요청
                    .timeout(30 * 1000) //30초 타임아웃
                    .followRedirects(true) //리다이렉트 시 리다이렉트 허용
                    .execute();
            return Jsoup.parse(response.body(), url);

        } catch (IOException e) {
            System.err.println("URL: " + url + " - HTML 문서 가져오기 실패: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
    //
    public String extractBaseURLFromURL(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String path = uri.getPath();
        int secondSlashIndex = path.indexOf('?', 0);
        String trimmedPath=path;
        if (secondSlashIndex > 0) {
            trimmedPath = path.substring(0, secondSlashIndex);
        }
        return uri.getScheme() + "://" + uri.getAuthority()+trimmedPath;
    }

}











