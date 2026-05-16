package com.example.security.application.port.out

import com.example.security.application.query.SearchQuery
import com.example.security.application.query.SearchResult

/** OpenSearch 검색 어댑터의 outbound port. */
interface EventSearchPort {

    fun search(query: SearchQuery): SearchResult
}
