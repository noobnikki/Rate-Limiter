# Eshopbox Backend Engineering Test — Sliding Window Rate Limiter

## Problem

Allow at most `N` requests in any rolling one-second window, even when many
threads call `allowRequest()` concurrently.

## Approach

I use a `Deque<Long>` containing timestamps of requests that are currently
inside the one-second sliding window.

For every call to `allowRequest()`:

1. Remove timestamps that are at least one second old.
2. If the deque already contains `N` timestamps, return `false`.
3. Otherwise, add the current timestamp and return `true`.

## Thread Safety

`allowRequest()` is `synchronized`.

This makes the cleanup, limit check, and timestamp insertion one atomic
operation. Therefore, two threads cannot both observe available capacity
and cause more than `N` requests to succeed.

## Complexity

- Time: `O(1)` amortized per request
- Space: `O(N)`

Each timestamp is inserted once and removed once.

## Assumption

I interpreted "no more than N calls per second" as a true sliding one-second
window rather than fixed wall-clock second buckets.

The tests also verify concurrent calls from many threads.
