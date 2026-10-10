package com.example.library.domain.repository;

import com.example.library.domain.catalog.LibraryItem;

import java.util.List;
import java.util.Optional;

public interface LibraryItemRepository {

    Optional<LibraryItem> findByBarcode(String barcode);

    List<LibraryItem> findByTitleId(Long titleId);

    LibraryItem save(LibraryItem item);
}
