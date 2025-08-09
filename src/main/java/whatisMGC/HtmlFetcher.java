package whatisMGC;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

public class HtmlFetcher {

    public Document getHTMLDocument(String url) {
        try{
            Connection.Response response =  Jsoup.connect(url).timeout(5000).execute();
            return Jsoup.parse(response.body(), url);

        } catch (IOException e) {
            System.err.println("URL: " + url + " - HTML 문서 가져오기 실패: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
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











