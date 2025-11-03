package com.example.food.app.cart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.food.app.cart.entity.Cart;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUser_Id(Long userId);

}
