package whatisMGC;

import java.util.Objects;

public class BoardPost {
    private String department;
    private String title;
    private String author;
    private String postDate;
    private String hits;
    private String absoluteUrl;
    private String content;
    private String attachment;

    // 생성자
    public BoardPost(String department, String title, String author, String postDate, String hits, String absoluteUrl, String content, String attachment) {
        this.department = department;
        this.title = title;
        this.author = author;
        this.postDate = postDate;
        this.hits=hits;
        this.absoluteUrl = absoluteUrl;
        this.content = content;
        this.attachment = attachment;
    }

    // Getter 및 Setter 메서드들
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getpostDate() { return postDate; }
    public void setpostDate(String postTime) { this.postDate = postDate; }
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
                ", postDate='" + postDate + '\'' +
                ", hits='" + hits + '\'' +
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

    public String getHits() {
        return hits;
    }

    public void setHits(String hits) {
        this.hits = hits;
    }

    public String getAttachment() {
        return attachment;
    }

    public void setAttachment(String attachment) {
        this.attachment = attachment;
    }
}