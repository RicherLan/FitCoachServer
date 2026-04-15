package com.lanprojects.fitcoach.login.repository;

import com.lanprojects.fitcoach.login.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUid(String uid);

    Optional<User> findByOpenId(String openId);

    Optional<User> findByUnionId(String unionId);

    Optional<User> findByPhone(String phone);

    boolean existsByOpenId(String openId);
}
