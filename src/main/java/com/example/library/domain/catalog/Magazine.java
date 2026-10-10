package com.example.library.domain.catalog;

import com.example.library.domain.shared.ItemType;

/** 杂志：区分中文杂志与外文杂志，含卷号与期号。 */
public class Magazine extends LibraryItem {

    private final String language;
    private String issue;
    private String volume;

    public Magazine(Long id, String barcode, Long titleId, ItemType itemType, String language) {
        super(id, barcode, titleId, itemType);
        this.language = language;
    }

    public String language() {
        return language;
    }

    public String issue() {
        return issue;
    }

    public String volume() {
        return volume;
    }
}
