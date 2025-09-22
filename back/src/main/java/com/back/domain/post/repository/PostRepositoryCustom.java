package com.back.domain.post.repository;

import com.back.domain.post.dto.PostSearchCondition;
import com.back.domain.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostRepositoryCustom {
    Page<Post> searchPosts(PostSearchCondition postSearchCondition, Pageable pageable);
}
