# Load testing

No load tests have been run, and Aegis makes no throughput, latency, or capacity claims.

## When load testing begins

Add a repeatable open-source workload (for example, k6) only after the proxy, rate-limit, and bounded-concurrency phases provide meaningful traffic behavior to measure. Record the exact application revision, configuration, environment, downstream behavior, command, duration, and raw results.

Planned scenarios:

1. Normal traffic
2. Traffic spike and queue saturation
3. Rate-limit saturation
4. Increased downstream latency
5. Downstream failures and retry behavior
6. Circuit-breaker activation and recovery

For each run, capture actual throughput, P50/P95/P99 latency, error rate, CPU, memory, queue depth, and relevant downstream/rate-limit metrics. Clearly distinguish measured results from hypotheses and do not reuse results across materially different environments.
