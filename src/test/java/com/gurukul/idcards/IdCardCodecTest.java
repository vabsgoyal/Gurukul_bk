package com.gurukul.idcards;

import com.gurukul.idcards.entity.IdCardOwnerType;
import com.gurukul.idcards.service.IdCardCodec;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdCardCodecTest {

	private static final String JWT_SECRET = "dev-only-insecure-secret-change-me-before-prod-32bytes!";
	private final UUID school = UUID.randomUUID();
	private final UUID person = UUID.randomUUID();
	private final IdCardCodec codec = new IdCardCodec("", JWT_SECRET);

	@Test
	void roundTripsASignedCode() {
		String code = codec.encode(IdCardOwnerType.STUDENT, person, school);
		assertThat(code).startsWith("GK1.S." + person + ".");
		var subject = codec.decode(code, school).orElseThrow();
		assertThat(subject.type()).isEqualTo(IdCardOwnerType.STUDENT);
		assertThat(subject.ownerId()).isEqualTo(person);

		String staff = codec.encode(IdCardOwnerType.EMPLOYEE, person, school);
		assertThat(codec.decode(staff, school).orElseThrow().type()).isEqualTo(IdCardOwnerType.EMPLOYEE);
	}

	@Test
	void rejectsTamperedCodes() {
		String code = codec.encode(IdCardOwnerType.STUDENT, person, school);
		String[] parts = code.split("\\.");
		// Someone else's id with this signature.
		assertThat(codec.decode(parts[0] + ".S." + UUID.randomUUID() + "." + parts[3], school)).isEmpty();
		// Student code relabelled as staff.
		assertThat(codec.decode(parts[0] + ".E." + parts[2] + "." + parts[3], school)).isEmpty();
		// One character of the signature changed.
		char last = parts[3].charAt(parts[3].length() - 1);
		String flipped = parts[3].substring(0, parts[3].length() - 1) + (last == 'A' ? 'B' : 'A');
		assertThat(codec.decode(parts[0] + ".S." + parts[2] + "." + flipped, school)).isEmpty();
		// Garbage.
		assertThat(codec.decode("hello", school)).isEmpty();
		assertThat(codec.decode("GK1.S.not-a-uuid.xyz", school)).isEmpty();
		assertThat(codec.decode("GK2.S." + parts[2] + "." + parts[3], school)).isEmpty();
		assertThat(codec.decode(null, school)).isEmpty();
	}

	@Test
	void aCodeFromAnotherSchoolDoesNotVerify() {
		String code = codec.encode(IdCardOwnerType.STUDENT, person, school);
		assertThat(codec.decode(code, UUID.randomUUID())).isEmpty();
	}

	@Test
	void keyComesFromTheDedicatedSecretWhenSetOtherwiseFromTheJwtSecret() {
		String derived = codec.encode(IdCardOwnerType.STUDENT, person, school);
		// Same JWT secret, no dedicated secret: same key (codes survive a restart).
		assertThat(new IdCardCodec(null, JWT_SECRET).decode(derived, school)).isPresent();
		// A different JWT secret derives a different key.
		assertThat(new IdCardCodec("", JWT_SECRET + "x").decode(derived, school)).isEmpty();
		// A dedicated secret is independent of the JWT secret.
		IdCardCodec dedicated = new IdCardCodec("a-dedicated-qr-secret-of-32-bytes!!", JWT_SECRET);
		String code = dedicated.encode(IdCardOwnerType.STUDENT, person, school);
		assertThat(code).isNotEqualTo(derived);
		assertThat(new IdCardCodec("a-dedicated-qr-secret-of-32-bytes!!", "another-jwt-secret").decode(code, school)).isPresent();
		assertThat(codec.decode(code, school)).isEmpty();
	}

}
