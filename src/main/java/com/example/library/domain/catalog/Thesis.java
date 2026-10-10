package com.example.library.domain.catalog;

import com.example.library.domain.shared.ItemType;

/** 论文：含学位与授予单位。 */
public class Thesis extends LibraryItem {

    private String degree;
    private String awardingUnit;

    public Thesis(Long id, String barcode, Long titleId) {
        super(id, barcode, titleId, ItemType.THESIS);
    }

    public String degree() {
        return degree;
    }

    public String awardingUnit() {
        return awardingUnit;
    }
}
