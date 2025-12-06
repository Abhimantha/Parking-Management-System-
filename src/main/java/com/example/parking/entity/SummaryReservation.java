package com.example.parking.entity;

import com.example.parking.dto.SummaryStatus;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "summery_reservation")
public class SummaryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // db id

    @Column(name = "external_id", nullable = false)
    private Long externalId; // user-entered ID

    @Column(length = 200)
    private String user;

    private Integer hours;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SummaryStatus status = SummaryStatus.ACTIVE;

    private LocalDate date;

    @Column(columnDefinition = "DECIMAL(10,2)")
    private Double amount;

    public Long getId() { return id; }
    public Long getExternalId() { return externalId; }
    public void setExternalId(Long externalId) { this.externalId = externalId; }
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


