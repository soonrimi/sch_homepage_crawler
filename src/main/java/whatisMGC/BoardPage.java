package whatisMGC;

import java.net.URISyntaxException;
import java.util.*;

public class
BoardPage {

    private String title;
    private String board_name;
    private String absolute_url;
    private String category;

    public BoardPage(String title, String absolute_url, String category) {
        this.title = title;
        this.board_name=null;
        this.absolute_url = absolute_url;
        this.category=category;
    }
    public BoardPage(String title, String board_name,String absolute_url, String category) {
        this.title = title;
        this.board_name=board_name;
        this.absolute_url = absolute_url;
        this.category=category;
    }


    // Getter 및 Setter 메서드들
    public String getTitle() { return title; }
    public String getAbsoluteUrl() { return absolute_url; }
    public String getCategory() { return category; }
    public String getBoardName() { return board_name; }

    public void setTitle(String title) { this.title = title; }
    public void setAbsoluteUrl(String absolute_url) { this.absolute_url = absolute_url; }
    public void setCategory(String category) { this.category = category; }
    public void setBoardName(String board_name) { this.board_name = board_name;}
    public static List<BoardPage> mergePages(List<BoardPage> announceBoardPages, List<BoardPage> majorPage, List<BoardPage> centerPage) {
        Set<BoardPage> Pages = new HashSet<>();

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
    public static List<BoardPage> add(List<BoardPage> pages, List<BoardPage> newPages) {
        Set<BoardPage> pageSet = new LinkedHashSet<>(pages);
        pageSet.addAll(newPages);
        pages.clear();
        pages.addAll(pageSet);

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
                ", boardName='" + board_name + '\'' +
                ", absoluteUrl='" + absolute_url + '\'' +
                ", category='" + category + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoardPage boardPage = (BoardPage) o;
        return Objects.equals(absolute_url, boardPage.absolute_url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(absolute_url);
    }



}

