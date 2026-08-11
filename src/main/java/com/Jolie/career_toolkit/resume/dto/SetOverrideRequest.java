package com.Jolie.career_toolkit.resume.dto;

/**
 * 針對這份履歷客製化措辭。
 * 傳空字串代表「清除客製化，回到原始積木內容」——跟不呼叫這個端點是不同的意圖。
 */
public record SetOverrideRequest(String content) {}
