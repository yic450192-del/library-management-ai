package com.example.library.domain.repository;

import com.example.library.domain.reader.Reader;

import java.util.Optional;

public interface ReaderRepository {

    Optional<Reader> findById(Long readerId);
}
