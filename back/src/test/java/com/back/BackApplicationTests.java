package com.back;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BackApplicationTests {

    @Test
    @DisplayName("dummy Test")
    void contextLoads() {
        int a = 1;

        assertThat(a).isEqualTo(2);
    }

}
