package com.example.library.domain.catalog;

import com.example.library.domain.shared.ItemType;

import java.util.List;
import java.util.Objects;

/**
 * 标题信息（书目）。与具体的馆藏副本分离。
 * 对应指导书"添加标题信息 / 删除标题信息"。
 * BR-015：题名下仍有副本时不可删除。
 */
public class BookTitle {

    private final Long id;
    private final String isbn;
    private String title;
    private String authors;
    private String publisher;
    private Integer publishedYear;
    private final ItemType itemType;

    public BookTitle(Long id, String isbn, String title, ItemType itemType) {
        this.id = id;
        this.isbn = Objects.requireNonNull(isbn, "ISBN 不能为空");
        this.title = Objects.requireNonNull(title, "书名不能为空");
        this.itemType = Objects.requireNonNull(itemType, "借出物类型不能为空");
    }

    /** 可借副本数：状态为 IN_LIBRARY 的副本数量。 */
    public long availableCopyCount(List<LibraryItem> items) {
        return items.stream().filter(LibraryItem::isAvailable).count();
    }

    public boolean hasAnyCopy(List<LibraryItem> items) {
        return !items.isEmpty();
    }

    public Long id() {
        return id;
    }

    public String isbn() {
        return isbn;
    }

    public String title() {
        return title;
    }

    public String authors() {
        return authors;
    }

    public String publisher() {
        return publisher;
    }

    public Integer publishedYear() {
        return publishedYear;
    }

    public ItemType itemType() {
        return itemType;
    }
}
