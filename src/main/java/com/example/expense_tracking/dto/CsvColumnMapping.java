package com.example.expense_tracking.dto;

import lombok.Data;

@Data
public class CsvColumnMapping {
    private String date;
    private String description;
    private String amount;
    private String type;
    private String category;
    private String currency;
}
