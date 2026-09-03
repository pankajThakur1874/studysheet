package net.tridha.studysheet.domain;

public enum StudyStatus {
    TO_STUDY("To Study", "text-stone-400 dark:text-[#6B7280]",
            "<svg width=\"18\" height=\"18\" viewBox=\"0 0 18 18\" fill=\"none\"><circle cx=\"9\" cy=\"9\" r=\"7\" stroke=\"#CFC8BC\" stroke-width=\"1.5\"/></svg>"),
    IN_PROGRESS("In Progress", "text-[#B45309]",
            "<svg width=\"18\" height=\"18\" viewBox=\"0 0 18 18\" fill=\"none\"><circle cx=\"9\" cy=\"9\" r=\"7\" stroke=\"#B45309\" stroke-width=\"1.5\"/><path d=\"M9 2a7 7 0 010 14z\" fill=\"#B45309\"/></svg>"),
    NEEDS_REVIEW("Needs Review", "text-[#B45309]",
            "<svg width=\"18\" height=\"18\" viewBox=\"0 0 18 18\" fill=\"none\"><circle cx=\"9\" cy=\"9\" r=\"7\" stroke=\"#B45309\" stroke-width=\"1.5\"/><path d=\"M9 5.5v4.2\" stroke=\"#B45309\" stroke-width=\"1.7\" stroke-linecap=\"round\"/><circle cx=\"9\" cy=\"12.4\" r=\"1\" fill=\"#B45309\"/></svg>"),
    MASTERED("Mastered", "text-slate-900 dark:text-white",
            "<svg width=\"18\" height=\"18\" viewBox=\"0 0 18 18\" fill=\"none\"><circle cx=\"9\" cy=\"9\" r=\"8\" fill=\"#101828\"/><path d=\"M5.6 9.2l2.3 2.3 4.5-4.6\" stroke=\"#FAF9F6\" stroke-width=\"1.6\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>");

    private final String displayName;
    private final String badgeCssClass;
    private final String svgIconHtml;

    StudyStatus(String displayName, String badgeCssClass, String svgIconHtml) {
        this.displayName = displayName;
        this.badgeCssClass = badgeCssClass;
        this.svgIconHtml = svgIconHtml;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeCssClass() {
        return badgeCssClass;
    }

    public String getSvgIconHtml() {
        return svgIconHtml;
    }
}
