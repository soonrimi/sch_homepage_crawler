package whatisMGC;

import java.net.URISyntaxException;
import java.util.*;

public class
BoardPage {

        private String title;
        private String absoluteUrl;
        private String categorize;

        public BoardPage(String title, String absoluteUrl, String categorize) {
            this.title = title;
            this.absoluteUrl = absoluteUrl;
            this.categorize=categorize;
        }


        // Getter 및 Setter 메서드들
        public String getTitle() { return title; }
        public String getAbsoluteUrl() { return absoluteUrl; }
        public String getCategorize() { return categorize; }

        public void setTitle(String title) { this.title = title; }
        public void setAbsoluteUrl(String absoluteUrl) { this.absoluteUrl = absoluteUrl; }
        public void setCategorize(String categorize) { this.categorize = categorize; }
        public static List<BoardPage> mergePages(List<BoardPage> announceBoardPages, List<BoardPage> majorPage, List<BoardPage> centerPage) {
            Set<BoardPage> Pages = new HashSet<>(); //중복제거

            if (announceBoardPages != null) {
                Pages.addAll(announceBoardPages);
            }
            if (majorPage != null) {
                Pages.addAll(majorPage);
            }
            if (centerPage != null) {
                Pages.addAll(centerPage);
            }

            return new ArrayList<>(Pages);
        }
        public static String getTitleByPages(List<BoardPage> boardPages, String url) {
            for (BoardPage boardPage : boardPages) {
                if (boardPage.getAbsoluteUrl().equals(url)) {
                    return boardPage.getTitle();
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return "BoardPage{" +
                    "title='" + title + '\'' +
                    ", absoluteUrl='" + absoluteUrl + '\'' +
                    ", categorize='" + categorize + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BoardPage boardPage = (BoardPage) o;
            return Objects.equals(absoluteUrl, boardPage.absoluteUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(absoluteUrl);
        }


    }

