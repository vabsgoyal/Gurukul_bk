package com.gurukul.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "Standard API response wrapper")
public class ApiResponse<T> {

	@Schema(description = "Whether the request succeeded", example = "true")
	private boolean success;

	@Schema(description = "Response payload on success; null on error")
	private T data;

	@Schema(description = "Human-readable message, often used for errors or confirmations", example = "Student created")
	private String message;

	@Schema(description = "Paginated list endpoints only: whether a further page exists beyond this one")
	private Boolean hasNext;

	@Schema(description = "Paginated list endpoints only: total row count across every page")
	private Long totalElements;

	@Schema(description = "Stable machine-readable code for specific error cases the client should "
			+ "branch on (e.g. \"GOOGLE_ACCOUNT_NOT_CONNECTED\"); null for generic errors and all "
			+ "successes - clients should fall back to displaying `message` when absent")
	private String errorCode;

	public ApiResponse(boolean success, T data, String message) {
		this.success = success;
		this.data = data;
		this.message = message;
	}

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static <T> ApiResponse<T> success(T data, String message) {
		return new ApiResponse<>(true, data, message);
	}

	public static <T> ApiResponse<T> error(String message) {
		return new ApiResponse<>(false, null, message);
	}

	public static <T> ApiResponse<T> error(String message, String errorCode) {
		ApiResponse<T> response = new ApiResponse<>(false, null, message);
		response.errorCode = errorCode;
		return response;
	}

	/**
	 * Backward-compatible pagination shape: {@code data} stays the literal array of this page's rows
	 * (exactly what pre-pagination clients already expect and parse - they'll just silently only see
	 * one page's worth instead of every row, no crash), with hasNext/totalElements added as sibling
	 * fields old clients simply ignore. New clients read those two fields to drive "load more".
	 * totalElements is nullable - it's only computed on page 0 to avoid a second DB round-trip on
	 * every subsequent page (see PageResponse); callers should carry forward the page-0 value.
	 */
	public static <T> ApiResponse<List<T>> page(List<T> content, boolean hasNext, Long totalElements) {
		ApiResponse<List<T>> response = new ApiResponse<>(true, content, null);
		response.hasNext = hasNext;
		response.totalElements = totalElements;
		return response;
	}

}
