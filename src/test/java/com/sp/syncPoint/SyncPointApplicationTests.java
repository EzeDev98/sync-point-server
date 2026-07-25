package com.sp.syncPoint;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;


@SpringBootTest
@ActiveProfiles("test")
@MockBean({JwtDecoder.class, RedissonClient.class})
class SyncPointApplicationTests {

	@Test
	void contextLoads() {
	}

}
