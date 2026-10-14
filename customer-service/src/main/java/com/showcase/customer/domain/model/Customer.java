package com.showcase.customer.domain.model;

import static com.showcase.customer.domain.model.Address.required;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.model.AuditedModel;
import com.showcase.customer.domain.exception.CustomerErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Cliente de la tienda con sus direcciones. Siempre tiene como mucho una dirección principal, y si tiene
 * direcciones, exactamente una.
 */
public class Customer extends AuditedModel {

    static final int MAX_ADDRESSES = 5;

    private final Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private final List<Address> addresses;

    public Customer(Long id, Long userId, String firstName, String lastName, String email, String phone,
                    List<Address> addresses, boolean active, Long version, Instant createdAt, Instant updatedAt) {
        super(id, active, version, createdAt, updatedAt);
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.addresses = new ArrayList<>(addresses == null ? List.of() : addresses);
    }

    /** {@code userId} es el usuario de auth-service; puede ser null para clientes dados de alta por un gestor. */
    public static Customer create(Long userId, String firstName, String lastName, String email, String phone) {
        return new Customer(null, userId, required(firstName, "firstName"), required(lastName, "lastName"),
                normalizeEmail(email), phone, List.of(), true, null, null, null);
    }

    public void update(String newFirstName, String newLastName, String newEmail, String newPhone) {
        this.firstName = required(newFirstName, "firstName");
        this.lastName = required(newLastName, "lastName");
        this.email = normalizeEmail(newEmail);
        this.phone = newPhone;
    }

    /** La primera dirección pasa a ser la principal. */
    public void addAddress(Address address) {
        if (addresses.size() >= MAX_ADDRESSES) {
            throw new BusinessRuleException(CustomerErrorCode.TOO_MANY_ADDRESSES, MAX_ADDRESSES);
        }
        address.markAsMain(addresses.isEmpty());
        addresses.add(address);
    }

    /** Si se borra la principal, la siguiente pasa a serlo. */
    public void removeAddress(Long addressId) {
        Address address = findAddress(addressId);
        addresses.remove(address);
        if (address.isMain() && !addresses.isEmpty()) {
            addresses.getFirst().markAsMain(true);
        }
    }

    public void setMainAddress(Long addressId) {
        Address target = findAddress(addressId);
        addresses.forEach(address -> address.markAsMain(address == target));
    }

    private Address findAddress(Long addressId) {
        return addresses.stream().filter(address -> Objects.equals(address.getId(), addressId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(CustomerErrorCode.ADDRESS_NOT_FOUND, addressId));
    }

    public static String normalizeEmail(String email) {
        return required(email, "email").toLowerCase(Locale.ROOT);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public List<Address> getAddresses() {
        return Collections.unmodifiableList(addresses);
    }
}
