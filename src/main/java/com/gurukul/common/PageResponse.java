package com.gurukul.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "A single page of results, for list endpoints with too many rows to return at once")
public class PageResponse<T> {

	@Schema(description = "This page's rows")
	private List<T> content;

	@Schema(description = "Whether a further page exists")
	private boolean hasNext;

	@Schema(description = "Total row count across every page")
	private long totalElements;

}
