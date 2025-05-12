package com.neptune.afo.repository;

import com.neptune.afo.entity.Story;
import com.neptune.afo.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface StoryRepository
    extends JpaRepository<Story, Long>, JpaSpecificationExecutor<Story> {
  List<Story> findByCreatedByAndFeaturedTrue(User user);
}
