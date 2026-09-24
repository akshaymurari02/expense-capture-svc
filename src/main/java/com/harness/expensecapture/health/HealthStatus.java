package com.harness.expensecapture.health;

/** Health of a single dependency, or of the service as a whole. */
public enum HealthStatus {

    /** Fully functional. */
    UP,

    /** Functional but impaired; traffic may still be served. */
    DEGRADED,

    /** Not functional. */
    DOWN
}
