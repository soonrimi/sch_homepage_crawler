package whatisMGC;

public enum Category {
    UNIVERSITY("대학"),
    DEPARTMENT("학과"),
    GRADE("학년"),
    RECRUIT("채용"),
    ACTIVITY("활동"),
    PROMOTION("홍보");

    private final String description;

    Category(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
