package com.example.parking.entity;

import com.example.parking.entity.enums.Role;
import com.example.parking.user.UserPasswordEntityListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "users")
@EntityListeners(UserPasswordEntityListener.class)
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Column(nullable = false)
    private String username;

    @Email @NotBlank @Column(nullable = false, unique = true)
    private String email;

    @NotBlank @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.DRIVER;

    // stays default false
    @Column(nullable = false)
    private boolean suspended = false;

    // NEW: optional phone
    @Column(length = 30)
    private String phone;

    // (If you already added these columns earlier, keep them mapped)
    @Column(name = "car_model", length = 50)
    private String carModel;

    @Column(name = "car_plate", length = 20)
    private String carPlate;

    @Column(name = "nic_number", length = 20)
    private String nicNumber;

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isSuspended() { return suspended; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCarModel() { return carModel; }
    public void setCarModel(String carModel) { this.carModel = carModel; }

    public String getCarPlate() { return carPlate; }
    public void setCarPlate(String carPlate) { this.carPlate = carPlate; }

    public String getNicNumber() { return nicNumber; }
    public void setNicNumber(String nicNumber) { this.nicNumber = nicNumber; }
}
