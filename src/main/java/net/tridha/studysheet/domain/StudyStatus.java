package net.tridha.studysheet.domain;

public enum StudyStatus {
    TO_STUDY("To Study", "bg-amber-100 dark:bg-amber-950 text-amber-800 dark:text-amber-300 border-amber-300 dark:border-amber-800"),
    IN_PROGRESS("In Progress", "bg-blue-100 dark:bg-blue-950 text-blue-800 dark:text-blue-300 border-blue-300 dark:border-blue-800"),
    NEEDS_REVIEW("Needs Review", "bg-rose-100 dark:bg-rose-950 text-rose-800 dark:text-rose-300 border-rose-300 dark:border-rose-800"),
    MASTERED("Mastered", "bg-emerald-100 dark:bg-emerald-950 text-emerald-800 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800");

    private final String displayName;
    private final String badgeCssClass;

    StudyStatus(String displayName, String badgeCssClass) {
        this.displayName = displayName;
        this.badgeCssClass = badgeCssClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeCssClass() {
        return badgeCssClass;
    }
}
