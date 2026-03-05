package dev.transmute.plugin

/**
 * Type-safe service registry for cross-plugin collaboration.
 *
 * Replaces the untyped `extras: MutableMap<String, Any>` with a strongly-typed
 * API where services are registered and retrieved via [ServiceKey].
 *
 * ```kotlin
 * val CACHE = ServiceKey<CacheService>("dev.transmute.cache")
 *
 * // Plugin A registers:
 * scope.services.register(CACHE, InMemoryCache())
 *
 * // Plugin B retrieves:
 * val cache = scope.services.get(CACHE)
 * val cacheOrNull = scope.services.getOrNull(CACHE)
 * ```
 */
class ServiceRegistry {
  private val services = mutableMapOf<String, Any>()

  /**
   * Register a service under the given [key].
   *
   * @throws IllegalStateException if a service with the same key is already registered.
   */
  fun <T : Any> register(key: ServiceKey<T>, service: T) {
    require(key.id !in services) {
      "Service already registered: ${key.id}"
    }
    services[key.id] = service
  }

  /**
   * Register a service, replacing any existing service with the same key.
   */
  fun <T : Any> replace(key: ServiceKey<T>, service: T) {
    services[key.id] = service
  }

  /**
   * Retrieve a service by key, or `null` if not registered.
   */
  @Suppress("UNCHECKED_CAST")
  fun <T : Any> getOrNull(key: ServiceKey<T>): T? = services[key.id] as? T

  /**
   * Retrieve a service by key.
   *
   * @throws IllegalStateException if the service is not registered.
   */
  fun <T : Any> get(key: ServiceKey<T>): T = getOrNull(key) ?: error("Service not registered: ${key.id}")

  /**
   * Check whether a service is registered under the given [key].
   */
  fun <T : Any> contains(key: ServiceKey<T>): Boolean = key.id in services

  /** All registered service keys. */
  fun keys(): Set<String> = services.keys.toSet()
}
