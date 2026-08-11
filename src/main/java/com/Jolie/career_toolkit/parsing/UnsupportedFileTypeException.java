package com.Jolie.career_toolkit.parsing;

public class UnsupportedFileTypeException extends RuntimeException {

    private final String detected;

    public UnsupportedFileTypeException(String detected) {
        super("Unsupported file type: " + detected);
        this.detected = detected;
    }

    public String getDetected() {
        return detected;
    }
}
