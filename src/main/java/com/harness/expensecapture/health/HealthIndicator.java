package com.harness.expensecapture.health;

/**
 * An infrastructure dependency that must be usable before this instance can serve traffic.
 *
 * <p>Only genuine prerequisites belong here — storage, caches, datastores, brokers. Ordinary application
 * components are not checked: they are constructed at startup and cannot fail independently at runtime, so
 * probing them would add noise without adding signal. Registering an indicator therefore means "readiness
 * depends on this", and there is no need for an optional flag.
 */
public interface HealthIndicator {

    /** Key this dependency appears under in the readiness response. */
    String name();

    /**
     * Runs the check.
     *
     * <p>Implementations must never throw: a broken dependency has to be reported as
     * {@link HealthStatus#DOWN} with a reason, not hide the state of every other dependency behind a 500.
     */
    DependencyHealth check();
}
