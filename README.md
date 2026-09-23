# Eshopbox Test — Sliding Window Rate Limiter

## Problem

The goal is to allow no more than `N` requests in any rolling one-second
window, even when many threads call `allowRequest()` concurrently.

The rate limiter is designed to sit immediately before an external
sales-channel API call. The caller can make the external call only when
`allowRequest()` returns `true`.

## Project Structure

```text
rate-limiter/
|
|-- pom.xml
|-- README.md
|-- .gitignore
|
`-- src/
    |-- main/java/com/eshopbox/ratelimiter/
    |   `-- RateLimiter.java
    |
    `-- test/java/com/eshopbox/ratelimiter/
        `-- RateLimiterTest.java
```

## Approach

This implementation uses an **exact timestamp-based sliding window**.

The `RateLimiter` stores three important pieces of state:

```text
limit               -> maximum requests allowed in the window
clock               -> source used to obtain the current time
requestTimestamps   -> timestamps of currently active allowed requests
```

`requestTimestamps` is a `Deque<Long>` implemented using `ArrayDeque`.
The oldest request is kept at the front and the newest request is added at
the back.

For every call to `allowRequest()`:

```text
                 allowRequest()
                       |
                       v
                Get current time
                       |
                       v
       Remove timestamps >= 1 second old
                       |
                       v
           Are active requests >= N?
                  /            \
                Yes             No
                 |               |
                 v               v
          return false      Add current timestamp
                                    |
                                    v
                               return true
```

The implementation therefore evaluates the requests in the previous rolling
1000 milliseconds rather than resetting a counter at fixed wall-clock second
boundaries.

## Thread Safety

`allowRequest()` is declared `synchronized`:

```java
public synchronized boolean allowRequest()
```

This is important because the following operations must be treated as one
atomic state transition:

```text
cleanup expired timestamps
        +
check current request count
        +
insert new timestamp
```

Without synchronization, two threads could both observe an available slot
and both add their requests.

For example, with a limit of `5` and four requests already active:

```text
Thread A -> sees 4 < 5
Thread B -> sees 4 < 5
Thread A -> adds request
Thread B -> adds request
```

That could incorrectly allow six requests. Synchronization prevents this
race for calls made through the same `RateLimiter` instance.



## Boundary Behavior

The implementation removes a timestamp when:

```java
now - timestamp >= WINDOW_MILLIS
```

and `WINDOW_MILLIS` is `1000`.

Therefore, a request that is **exactly 1000 ms old is considered expired**.
The effective window is:

```text
(now - 1000 ms, now]
```

A dedicated boundary test verifies this behavior.

## Tests

The test suite is in `RateLimiterTest.java`.

### Request Limit test

`shouldAllowUpToConfiguredLimit()`

Checks that the limiter allows exactly `N` requests and rejects the next
request while all requests are inside the window.

### Reset / sliding-window expiration test

`shouldAllowAgainAfterOneSecondSlidesPast()`

Checks that once the oldest request leaves the one-second sliding window,
new capacity becomes available.

### Boundary test

`shouldExpireRequestAtExactlyOneSecondBoundary()`

Checks the exact `1000 ms` boundary and protects the behavior defined by the
`>= WINDOW_MILLIS` condition.

### Thread-safety test

`shouldBeThreadSafe()`

Starts many concurrent threads against the same limiter and verifies that
no more than the configured limit succeeds.

For example, with `100` threads and a limit of `10`, exactly `10` requests
should succeed.

### Invalid-input test

`shouldRejectInvalidLimit()`

Checks that zero and negative limits are rejected with
`IllegalArgumentException`.

## Complexity

- Time: `O(1)` amortized per request
- Space: `O(N)`

Each timestamp is inserted once and removed once.