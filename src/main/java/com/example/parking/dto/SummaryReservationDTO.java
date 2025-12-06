package com.example.parking.dto;

import java.time.LocalDate;

public class SummaryReservationDTO {
    private Long dbId; // database id
    private Long id;
    private String user;
    private Integer hours;
    private SummaryStatus status;
    private LocalDate date;
    private Double amount;
    public Long getDbId() { return dbId; }
    public void setDbId(Long dbId) { this.dbId = dbId; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }

    public SummaryStatus getStatus() { return status; }
    public void setStatus(SummaryStatus status) { this.status = status; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
}



