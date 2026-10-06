package com.disasterrelief.allocator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import com.disasterrelief.allocator.domain.ResourceRequestItem;

@SpringBootTest
class DisasterReliefBackendApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void requestItemRejectsZeroQuantity() {
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
		ResourceRequestItem item = new ResourceRequestItem();
		item.setRequestedQuantity(BigDecimal.ZERO);

		assertThat(validator.validate(item).stream()
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("requestedQuantity")))
				.isTrue();
	}

}
