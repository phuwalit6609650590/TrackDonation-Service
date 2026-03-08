package com.project.trackdonation.repository;

import com.project.trackdonation.entity.DonationReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonationReceiptRepository extends JpaRepository<DonationReceipt, String> {
}