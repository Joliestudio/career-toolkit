package com.Jolie.career_toolkit.application;

import java.util.Set;
import java.util.stream.Collectors;

public class IllegalStatusTransitionException extends RuntimeException {

    private final ApplicationStatus from;
    private final ApplicationStatus to;
    private final Set<ApplicationStatus> allowed;

    public IllegalStatusTransitionException(ApplicationStatus from, ApplicationStatus to) {
        super("Illegal transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
        this.allowed = from.nextStates();
    }

    public ApplicationStatus getFrom() { return from; }
    public ApplicationStatus getTo() { return to; }
    public Set<ApplicationStatus> getAllowed() { return allowed; }

    /**
     * 錯誤訊息一定要說得出「那我可以改成什麼」。
     * 只說「不能這樣改」的話，使用者只能一個一個試。
     */
    public String describeAllowed() {
        if (allowed.isEmpty()) {
            return from + " 是終態，不能再改成其他狀態";
        }
        return "從 " + from + " 只能改成："
                + allowed.stream().map(Enum::name).sorted().collect(Collectors.joining("、"));
    }
}
