package com.shopsphere.user;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.NotFoundException;
import com.shopsphere.user.UserDtos.AddressRequest;
import com.shopsphere.user.UserDtos.AddressResponse;

@Service
public class AddressService {

    private final AddressRepository addresses;
    private final UserRepository users;

    public AddressService(AddressRepository addresses, UserRepository users) {
        this.addresses = addresses;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addresses.findByUserIdOrderByIdAsc(userId).stream().map(AddressResponse::from).toList();
    }

    @Transactional
    public AddressResponse add(Long userId, AddressRequest req) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
        List<Address> existing = addresses.findByUserIdOrderByIdAsc(userId);
        // The first address is always the default; otherwise only when asked.
        boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(req.makeDefault());
        if (makeDefault) {
            existing.forEach(a -> a.setDefaultAddress(false));
        }
        Address saved = addresses.save(new Address(user, req.line1(), req.line2(), req.city(), req.state(),
                req.postalCode(), req.country(), makeDefault));
        return AddressResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Address getOwned(Long userId, Long addressId) {
        return addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Address", addressId));
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        addresses.delete(getOwned(userId, addressId));
    }
}
