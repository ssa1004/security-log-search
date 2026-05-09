package com.example.security.application.service;

import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.port.in.AggregateLogStatsUseCase;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.application.port.out.EventStatsPort;
import com.example.security.application.query.StatsQuery;
import com.example.security.application.query.StatsResult;
import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** use case 3 — ClickHouse 시계열 / 집계 query. */
@Service
public class AggregateLogStatsService implements AggregateLogStatsUseCase {

  private final EventStatsPort statsPort;
  private final AuditLogPort audit;
  private final Clock clock;

  public AggregateLogStatsService(EventStatsPort statsPort, AuditLogPort audit, Clock clock) {
    this.statsPort = statsPort;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  public StatsResult aggregate(StatsQuery query, OperatorContext operator) {
    if (!operator.canQueryOtherTenant() && !operator.tenantId().equals(query.tenantId())) {
      throw new TenantMismatchException(operator.tenantId(), query.tenantId());
    }
    var result = statsPort.aggregate(query);
    audit.append(
        new AuditEntry(
            UUID.randomUUID(),
            query.tenantId(),
            clock.instant(),
            operator.subject(),
            roleString(operator),
            AuditAction.STATS_QUERY,
            "stats",
            query.bucket().name(),
            operator.sourceIp(),
            Map.of(
                "from", query.from().toString(),
                "to", query.to().toString(),
                "bucket", query.bucket().name(),
                "groupBy", String.valueOf(query.groupByField()))));
    return result;
  }

  private static String roleString(OperatorContext operator) {
    return operator.roles().stream().map(Enum::name).collect(Collectors.joining(","));
  }
}
