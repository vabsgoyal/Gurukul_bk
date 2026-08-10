package com.gurukul.calls.jitsi;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Fixes the real-world gap in the calls feature: meet.jit.si has required an authenticated
 * participant to "start" a brand-new room since August 2023 (confirmed by manually driving a
 * headless browser against a fresh anonymous room - it shows "The conference has not yet started
 * because no moderators have yet arrived... please log-in" instead of connecting). Since every
 * call in this app generates a cryptographically random, never-before-seen room name, every call
 * hits that wall without this bot.
 *
 * <p>The bot does NOT script the login itself - Google/GitHub actively fight automated login
 * (CAPTCHAs, "suspicious sign-in" blocks), so scripting it per-call would be unreliable and get
 * the account flagged. Instead a human completes the OAuth login exactly once, in a real browser
 * pointed at {@code app.jitsi.bot.profile-dir} (see docs/jitsi-bot-setup.md); this service just
 * replays that already-authenticated Chrome profile headlessly to visit (and thereby "start")
 * each new room a moment before the real caller/callee join it. Once a room has been started this
 * way, Jitsi lets everyone else join anonymously with no moderator requirement - the bot doesn't
 * need to stay in the call, so it quits immediately after confirming the room started.
 *
 * <p>When the bot feature is disabled/unconfigured (blank profile dir), {@link #warmRoom} is a
 * no-op that returns {@code true} - calls proceed exactly as before this bot existed. But when the
 * feature IS configured and warming fails (expired session, Chrome missing, timeout), it returns
 * {@code false} so the caller can refuse to start a call that would otherwise land both
 * participants on Jitsi's "waiting for moderator... please log-in" screen.
 */
@Service
@EnableConfigurationProperties(JitsiBotProperties.class)
public class JitsiBotService {

	private static final Logger log = LoggerFactory.getLogger(JitsiBotService.class);
	private static final String STILL_WAITING_MARKER = "no moderators have yet arrived";
	private static final long POLL_INTERVAL_MILLIS = 1000L;

	private final JitsiBotProperties properties;

	public JitsiBotService(JitsiBotProperties properties) {
		this.properties = properties;
	}

	public boolean warmRoom(String roomName) {
		if (!properties.isConfigured()) {
			log.debug("Jitsi bot not configured (disabled or no profile-dir) - skipping warm for room {}", roomName);
			return true;
		}
		WebDriver driver = null;
		try {
			driver = newDriver();
			driver.get(roomUrl(roomName));
			boolean started = waitUntilStarted(driver);
			if (!started) {
				log.warn("Jitsi bot could not confirm room {} started within {}s - refusing to start the call. "
						+ "Check that the bot's saved session (app.jitsi.bot.profile-dir) is still logged in.",
						roomName, properties.warmTimeoutSeconds());
			}
			return started;
		} catch (Exception e) {
			log.error("Jitsi bot failed to warm room {} - refusing to start the call", roomName, e);
			return false;
		} finally {
			if (driver != null) {
				try {
					driver.quit();
				} catch (Exception e) {
					log.warn("Jitsi bot driver failed to quit cleanly", e);
				}
			}
		}
	}

	String roomUrl(String roomName) {
		return properties.baseUrl() + "/" + roomName
				+ "#config.prejoinConfig.enabled=false"
				+ "&config.disableDeepLinking=true"
				+ "&config.startWithAudioMuted=true"
				+ "&config.startWithVideoMuted=true"
				+ "&userInfo.displayName=%22Gurukul%22";
	}

	private WebDriver newDriver() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments(
				"--headless=new",
				"--use-fake-ui-for-media-stream",
				"--use-fake-device-for-media-stream",
				"--no-sandbox",
				"--disable-dev-shm-usage",
				"--user-data-dir=" + properties.profileDir());
		if (properties.chromeBinary() != null && !properties.chromeBinary().isBlank()) {
			options.setBinary(properties.chromeBinary());
		}

		if (properties.chromedriverPath() != null && !properties.chromedriverPath().isBlank()) {
			ChromeDriverService service = new ChromeDriverService.Builder()
					.usingDriverExecutable(new File(properties.chromedriverPath()))
					.build();
			return new ChromeDriver(service, options);
		}
		return new ChromeDriver(options);
	}

	private boolean waitUntilStarted(WebDriver driver) {
		long deadline = System.currentTimeMillis() + properties.warmTimeoutSeconds() * 1000L;
		while (System.currentTimeMillis() < deadline) {
			if (!stillWaitingForModerator(bodyText(driver))) {
				return true;
			}
			try {
				Thread.sleep(POLL_INTERVAL_MILLIS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	private String bodyText(WebDriver driver) {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (Exception e) {
			return "";
		}
	}

	/** Extracted as a pure function so the detection rule is unit-testable without a live browser. */
	static boolean stillWaitingForModerator(String bodyText) {
		return bodyText != null && bodyText.contains(STILL_WAITING_MARKER);
	}

}
