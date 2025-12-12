package com.example.warningapi.repository;

import com.example.warningapi.model.TestEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TestRepository extends JpaRepository<TestEntry, Integer> {
    List<TestEntry> findAllByOrderByTimestampDesc();
}
