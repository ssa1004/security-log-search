#!/usr/bin/env bash
# Canonicalize an OpenAPI spec so the drift gate is deterministic, not flaky.
#
# springdoc emits enum value order (and the relative position of `default:`)
# non-deterministically across environments — purely cosmetic ordering that is
# NOT a real API change. A byte-diff gate over the raw output therefore
# false-positives. This transform makes the spec canonical:
#
#   1) recursively sort every mapping's keys      -> key order is stable
#   2) sort every `enum` array                     -> enums are unordered sets,
#                                                      so sorting is semantically safe
#
# Applied identically by local regeneration (docs/openapi/README.md) and by the
# CI drift gate (.github/workflows/ci.yml) so both sides compare canonically.
# The gate stays strict (byte-diff AFTER normalize), so real API-surface changes
# (added/removed endpoints, params, enum *values*) still fail it.
#
# Usage: normalize-openapi.sh <spec.yaml>   # edits the file in place
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <spec.yaml>" >&2
  exit 2
fi
spec="$1"

# 1) recursively sort mapping keys
yq -P -i '(.. | select(tag == "!!map")) |= sort_keys(.)' "$spec"
# 2) sort every enum array (enums are unordered sets)
yq -P -i '(.. | select(has("enum")).enum) |= sort' "$spec"

# sanity: still a valid OpenAPI 3 document with paths
if [ "$(yq '.openapi != null and .paths != null' "$spec")" != "true" ]; then
  echo "::error::normalize produced an invalid OpenAPI document: $spec" >&2
  exit 1
fi
