package com.shopsphere.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 200)
    private String line1;

    @Column(length = 200)
    private String line2;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 80)
    private String state;

    @Column(nullable = false, length = 12)
    private String postalCode;

    @Column(nullable = false, length = 60)
    private String country;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    protected Address() {
    }

    public Address(User user, String line1, String line2, String city, String state,
                   String postalCode, String country, boolean defaultAddress) {
        this.user = user;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
        this.defaultAddress = defaultAddress;
    }

    /** Single-line form copied onto an order, so later address edits never change past orders. */
    public String toSnapshot() {
        String second = (line2 == null || line2.isBlank()) ? "" : ", " + line2;
        return line1 + second + ", " + city + ", " + state + " " + postalCode + ", " + country;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(boolean defaultAddress) { this.defaultAddress = defaultAddress; }
}
