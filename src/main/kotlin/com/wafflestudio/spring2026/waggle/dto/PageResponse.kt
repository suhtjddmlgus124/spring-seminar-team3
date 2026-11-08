package com.wafflestudio.spring2026.waggle.dto

class InvalidPageRequestException(message: String): RuntimeException(message)

/**
 * 목록 세 개(내 수강 목록, 가입 신청 목록, 세미나 목록)가 함께 쓰는 쿼리 파라미터.
 * page 는 0부터 센다. size 는 기본 20, 최대 100 이다.
 */
data class PageQuery(
    val page: Int,
    val size: Int,
) {
    val offset: Long get() = page.toLong() * size

    companion object {
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100

        fun of(page: Int?, size: Int?): PageQuery {
            val resolvedPage = page ?: 0
            val resolvedSize = size ?: DEFAULT_SIZE
            if (resolvedPage < 0) throw InvalidPageRequestException("page 는 0 이상이어야 합니다.")
            if (resolvedSize !in 1..MAX_SIZE) throw InvalidPageRequestException("size 는 1 이상 ${MAX_SIZE} 이하여야 합니다.")
            return PageQuery(resolvedPage, resolvedSize)
        }
    }
}

/**
 * Spring 의 Page 를 그대로 내보내면 필드 이름이 명세와 달라진다(page 대신 number 등).
 * 그래서 명세 형식대로 직접 만든다.
 */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    fun <R> map(transform: (T) -> R): PageResponse<R> =
        PageResponse(content.map(transform), page, size, totalElements, totalPages)

    companion object {
        fun <T> of(content: List<T>, query: PageQuery, totalElements: Long): PageResponse<T> =
            PageResponse(
                content = content,
                page = query.page,
                size = query.size,
                totalElements = totalElements,
                // 올림 나눗셈. 항목이 없으면 0 이다.
                totalPages = ((totalElements + query.size - 1) / query.size).toInt(),
            )
    }
}
