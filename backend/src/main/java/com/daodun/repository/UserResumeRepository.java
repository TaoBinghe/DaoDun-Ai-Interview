package com.daodun.repository;

import com.daodun.entity.UserResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserResumeRepository extends JpaRepository<UserResume, Long> {

    List<UserResume> findByUserIdOrderByCreateTimeDesc(Long userId);

    Optional<UserResume> findByIdAndUserId(Long id, Long userId);
}
