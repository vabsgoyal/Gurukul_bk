package com.gurukul.auth;

import com.gurukul.auth.whatsapp.OtpChannel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Test double for OtpChannel: "sends" nothing, just remembers the last code generated per phone so
 * tests can read it back instead of relying on the old hardcoded "1234". Always reports configured
 * so OtpService always calls send() rather than falling back to its dev-only log line.
 */
@TestConfiguration
public class CapturingOtpChannel implements OtpChannel {

	private final ConcurrentMap<String, String> lastCodeByPhone = new ConcurrentHashMap<>();

	@Bean
	@Primary
	public OtpChannel otpChannel() {
		return this;
	}

	@Override
	public boolean isConfigured() {
		return true;
	}

	@Override
	public void send(String phone, String otp) {
		lastCodeByPhone.put(phone, otp);
	}

	public String lastCodeFor(String phone) {
		String code = lastCodeByPhone.get(phone);
		if (code == null) {
			throw new IllegalStateException("No OTP was sent for " + phone);
		}
		return code;
	}

}
