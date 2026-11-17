# resumen archivo.json -> "rps p95_ms fallos_pct peticiones"
resumen() {
  jq -r '[(.metrics.http_reqs.rate|floor), (.metrics.http_req_duration["p(95)"]|.*10|round/10), ((.metrics.http_req_failed.value // 0)*1000|round/10), .metrics.http_reqs.count] | @tsv' "$1"
}
