package com.lanprojects.fitcoach.membership.repository;

import com.lanprojects.fitcoach.membership.entity.UserMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMembershipRepository extends JpaRepository<UserMembership, Long> {

    Optional<UserMembership> findByUserId(Long userId);
}
