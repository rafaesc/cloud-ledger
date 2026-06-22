package com.getcloudledger.api.shared.adapter.out.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountRecordEntity {
    @Id
    private String id;
    private String ownerId;
    private String currency;
}
