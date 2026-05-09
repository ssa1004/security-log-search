package com.example.security.application.port.out;

import com.example.security.application.query.SearchQuery;
import com.example.security.application.query.SearchResult;

/** OpenSearch 검색 어댑터의 outbound port. */
public interface EventSearchPort {

  SearchResult search(SearchQuery query);
}
