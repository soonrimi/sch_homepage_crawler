package whatisMGC;

import java.net.URISyntaxException;
import java.util.*;

public class
BoardPage {

        private String title;
        private String boardName;
        private String absoluteUrl;
        private String category;

        public BoardPage(String title, String absoluteUrl, String category) {
            this.title = title;
            this.boardName=null;
            this.absoluteUrl = absoluteUrl;
            this.category=category;
        }
        public BoardPage(String title, String boardName,String absoluteUrl, String category) {
            this.title = title;
            this.boardName=boardName;
            this.absoluteUrl = absoluteUrl;
            this.category=category;
        }


        // Getter 및 Setter 메서드들
        public String getTitle() { return title; }
        public String getAbsoluteUrl() { return absoluteUrl; }
        public String getCategory() { return category; }
        public String getBoardName() { return boardName; }

        public void setTitle(String title) { this.title = title; }
        public void setAbsoluteUrl(String absoluteUrl) { this.absoluteUrl = absoluteUrl; }
        public void setCategory(String category) { this.category = category; }
        public String setBoardName() { return boardName; }
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
        public static List<BoardPage> add(List<BoardPage> pages, List<BoardPage> newPages){
            for(BoardPage page:pages){
                for ( BoardPage newpage:newPages){
                    if(page.getAbsoluteUrl().equals(newpage.getAbsoluteUrl())){
                        newPages.remove(newpage);
                    }
                }
            }
            pages.addAll(newPages);
            return pages;
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
                    ", boardName='" + boardName + '\'' +
                    ", absoluteUrl='" + absoluteUrl + '\'' +
                    ", category='" + category + '\'' +
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

