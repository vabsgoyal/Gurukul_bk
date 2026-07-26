package com.gurukul.auth.security;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class AuthPrincipal {

	private final UUID ownerId;
	private final OwnerType ownerType;
	private final Role role;
	private final UUID schoolId;
	private final String username;

}
