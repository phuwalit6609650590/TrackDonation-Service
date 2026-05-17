package com.project.trackdonation.entity;

public enum AllocationStatus {
    SUCCESS,
    FAILED,
    CONFIRMED,
    WAITING_FOR_TRANSPORT,
    SELF_PICKUP,
    DISPATCHED,
    DEAD_LETTER_FAILED,
    DATA_INTEGRITY_ERROR,
    CANCELLED
}