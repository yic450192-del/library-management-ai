package com.example.library.domain.catalog;

import com.example.library.domain.shared.ItemType;

/** 图书：区分中文图书与外文图书。 */
public class Book extends LibraryItem {

    private final String language;
    private String edition;
    private Integer pages;

    public Book(Long id, String barcode, Long titleId, ItemType itemType, String language) {
        super(id, barcode, titleId, itemType);
        this.language = language;
    }

    public String language() {
        return language;
    }

    public String edition() {
        return edition;
    }

    public Integer pages() {
        return pages;
    }
}
