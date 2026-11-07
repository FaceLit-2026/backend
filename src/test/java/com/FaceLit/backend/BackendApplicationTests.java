package com.FaceLit.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
		"jwt.secret=application-context-test-secret-at-least-thirty-two-characters",
		"spring.mail.username=test@example.invalid", "spring.mail.password=unused"
})
@ActiveProfiles("test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
