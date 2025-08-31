// Attachment.java
package whatisMGC;

public class Attachment {
    private String fileName;
    private String fileUrl;

    public Attachment(String fileName, String fileUrl) {
        this.fileName = fileName;
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileUrl() {
        return fileUrl;
    }
}