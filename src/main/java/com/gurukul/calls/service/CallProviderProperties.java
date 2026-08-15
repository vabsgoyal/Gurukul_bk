package com.gurukul.calls.service;

import com.gurukul.calls.entity.CallProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Which provider new calls should prefer - defaults to JITSI (today's behavior) so this is a
 *  no-op until explicitly switched via CALL_PREFERRED_PROVIDER. Google Meet is only actually used
 *  when this is GOOGLE_MEET AND the specific host has connected their own Google account (see
 *  CallProviderResolver) - it's never a hard requirement that breaks calls for hosts who haven't
 *  connected yet. */
@ConfigurationProperties(prefix = "app.calls")
public record CallProviderProperties(@DefaultValue("JITSI") CallProvider preferredProvider) {
}
