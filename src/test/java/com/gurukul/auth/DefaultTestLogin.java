package com.gurukul.auth;

import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.JwtService;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Tests only. Every API route needs a login now, but most tests set up their data (students, staff,
 * sections) with bare requests that used to rely on the old open-by-default rule. A MockMvc request
 * with no Authorization header runs as that school's admin - the seeded dev admin (DevAdminSeeder),
 * or for a school a test registered itself, the admin that registration created - so setup keeps
 * working without every test logging in first.
 *
 * <p>A request that sets its own Authorization header keeps it - every role and permission test does.
 * To send a genuinely anonymous request, set the header to an empty string.
 */
@Component
public class DefaultTestLogin implements MockMvcBuilderCustomizer {

	private final CredentialRepository credentialRepository;
	private final JwtService jwtService;

	public DefaultTestLogin(CredentialRepository credentialRepository, JwtService jwtService) {
		this.credentialRepository = credentialRepository;
		this.jwtService = jwtService;
	}

	@Override
	public void customize(ConfigurableMockMvcBuilder<?> builder) {
		builder.defaultRequest(get("/").with(request -> {
			String schoolId = request.getHeader("X-School-Id");
			if (request.getHeader(HttpHeaders.AUTHORIZATION) == null && isUuid(schoolId)) {
				adminOf(UUID.fromString(schoolId)).ifPresent(admin -> request.addHeader(HttpHeaders.AUTHORIZATION,
						"Bearer " + jwtService.generateToken(admin)));
			}
			return request;
		}));
	}

	/** A malformed X-School-Id is left for SchoolContextFilter to reject, as in production. */
	private static boolean isUuid(String value) {
		try {
			return value != null && UUID.fromString(value) != null;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private Optional<Credential> adminOf(UUID schoolId) {
		return credentialRepository.findBySchoolIdAndUsername(schoolId, AuthTestSupport.DEV_ADMIN_USERNAME)
				.or(() -> credentialRepository.findAllBySchoolIdAndOwnerType(schoolId, OwnerType.EMPLOYEE).stream()
						.filter(c -> c.getRole() == Role.ADMIN && c.isEnabled())
						.findFirst());
	}

}
