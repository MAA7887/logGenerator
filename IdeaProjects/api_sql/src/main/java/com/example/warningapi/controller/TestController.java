package com.example.warningapi.controller;

import com.example.warningapi.model.TestEntry;
import com.example.warningapi.repository.TestRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rule")
public class TestController {

    private final TestRepository repository;

    public TestController(TestRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<TestEntry> getAll() {
        return repository.findAllByOrderByTimestampDesc();
    }
}
