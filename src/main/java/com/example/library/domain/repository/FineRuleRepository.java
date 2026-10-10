package com.example.library.domain.repository;

import com.example.library.domain.fine.FineRule;
import com.example.library.domain.shared.ItemType;

/** 按借出物类型取罚款规则（指导书需求 7）。 */
public interface FineRuleRepository {

    FineRule findByItemType(ItemType itemType);
}
