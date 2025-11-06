package whatisMGC;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;


public class BoardPost {
    private String department;
    private String title;
    private String author;
    private Timestamp postDate;
    private Integer hits;
    private String absoluteUrl;
    private String content;
    private List<String> contentImageUrls;
    private List<Attachment> attachments;
    private Category category;
    private String contentHash;

    // 생성자
    public BoardPost(String department, String title, String author, Timestamp postDate, Integer hits, String absoluteUrl, String content, List<String> contentImageUrls, List<Attachment> attachments, Category category, String contentHash) {
        this.department = department;
        this.title = title;
        this.author = author;
        this.postDate = postDate;
        this.hits = hits;
        this.absoluteUrl = absoluteUrl;
        this.content = content;
        this.contentImageUrls = contentImageUrls;
        this.attachments = attachments;
        this.category = category;
        this.contentHash = contentHash;
    }

    // Getter 및 Setter 메서드들
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Timestamp getpostDate() { return postDate; }
    public void setpostDate(Timestamp postTime) {this.postDate = postTime; }
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
        return Objects.equals(absoluteUrl, boardPost.absoluteUrl);
    }
    @Override
    public int hashCode() {
        return Objects.hash(absoluteUrl);
    }

    public Integer getHits() {
        return hits;
    }

    public void setHits(Integer hits) {
        this.hits = hits;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public List<String> getContentImageUrls() {
        return contentImageUrls;
    }

    public void setContentImageUrls(List<String> contentImageUrls) {
        this.contentImageUrls = contentImageUrls;
    }
}