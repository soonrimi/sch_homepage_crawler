package whatisMGC;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;


public class BoardPost {
    private String department;
    private String title;
    private String author;
    private Timestamp postDate;
    private String hits;
    private String absoluteUrl;
    private String content;
    private List<Attachment> attachments;
    private Category category;

    // 생성자
    public BoardPost(String department, String title, String author, Timestamp postDate, String hits, String absoluteUrl, String content, List<Attachment> attachments, Category category) {
        this.department = department;
        this.title = title;
        this.author = author;
        this.postDate = postDate;
        this.hits = hits;
        this.absoluteUrl = absoluteUrl;
        this.content = content;
        this.attachments = attachments;
        this.category = category;
    }

    // Getter 및 Setter 메서드들
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Timestamp getpostDate() { return postDate; }
    public void setpostDate(Timestamp postTime) { this.postDate = postDate; }
    public String getAbsoluteUrl() { return absoluteUrl; }
    public void setAbsoluteUrl(String absoluteUrl) { this.absoluteUrl = absoluteUrl; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() {return (this.category != null) ? this.category.name() : Category.DEPARTMENT.name(); }
    public void setCategory(Category category) { this.category = category; }

    @Override
    public String toString() {
        return "BoardPost{" +
                "department='" + department + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", postDate='" + postDate + '\'' +
                ", hits='" + hits + '\'' +
                ", absoluteUrl='" + absoluteUrl + '\'' +
                ", attachments=" + attachments + '\'' +
                ", category=" + category + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoardPost boardPost = (BoardPost) o;
        // title, author, department가 모두 같을 경우 true 반환
        return Objects.equals(title, boardPost.title) &&
                Objects.equals(author, boardPost.author);
    }
    @Override
    public int hashCode() {
        // title, author, department를 기준으로 해시 코드 생성
        return Objects.hash(title, author);
    }

    public String getHits() {
        return hits;
    }

    public void setHits(String hits) {
        this.hits = hits;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }
}