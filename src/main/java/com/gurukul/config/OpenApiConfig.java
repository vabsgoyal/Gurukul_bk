package com.gurukul.config;

import com.gurukul.schools.controller.SchoolController;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI gurukulOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Gurukul Backend API")
						.description("""
								REST API for Gurukul school management.
								
								All endpoints return an `ApiResponse` wrapper with `success`, `data`, and `message`.
								Every tenant-scoped `/api/v1/**` request requires the `X-School-Id` header.
								School registration endpoints (`POST/GET /api/v1/schools`) do not require it.
								""")
						.version("v1")
						.contact(new Contact().name("Gurukul").email("admin@gurukul.demo")));
	}

	@Bean
	public OperationCustomizer schoolIdHeaderCustomizer() {
		Parameter schoolIdHeader = new Parameter()
				.in("header")
				.name(SchoolContextFilter.SCHOOL_ID_HEADER)
				.description("School tenant UUID — required on all API requests")
				.required(true)
				.example("11111111-1111-1111-1111-111111111111");

		return (operation, handlerMethod) -> {
			if (handlerMethod.getBeanType().equals(SchoolController.class)) {
				return operation;
			}
			operation.addParametersItem(schoolIdHeader);
			return operation;
		};
	}

}
