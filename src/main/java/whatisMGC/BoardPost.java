package whatisMGC;

import java.util.Objects;

public class BoardPost {
    private String department;
    private String title;
    private String author;
    private String postTime;
    private String absoluteUrl;
    private String content;

    // 생성자
    public BoardPost(String department, String title, String author, String postTime, String absoluteUrl, String content) {
        this.department = department;
        this.title = title;
        this.author = author;
        this.postTime = postTime;
        this.absoluteUrl = absoluteUrl;
        this.content = content;
    }

    // Getter 및 Setter 메서드들
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getPostTime() { return postTime; }
    public void setPostTime(String postTime) { this.postTime = postTime; }
    public String getAbsoluteUrl() { return absoluteUrl; }
    public void setAbsoluteUrl(String absoluteUrl) { this.absoluteUrl = absoluteUrl; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    @Override
    public String toString() {
        return "BoardPost{" +
                "department='" + department + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", postTime='" + postTime + '\'' +
                ", absoluteUrl='" + absoluteUrl + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoardPost boardPost = (BoardPost) o;
        return Objects.equals(absoluteUrl, boardPost.absoluteUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(absoluteUrl);
    }
}