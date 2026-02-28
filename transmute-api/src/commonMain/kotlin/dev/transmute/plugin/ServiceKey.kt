package dev.transmute.plugin

/**
 * A type-safe key for registering and retrieving services from a [ServiceRegistry].
 *
 * Services allow plugins to share capabilities with each other in a type-safe
 * manner, replacing the untyped `extras: MutableMap<String, Any>`.
 *
 * ```kotlin
 * // Declare a service key (typically a top-level val):
 * val MY_SERVICE = ServiceKey<MyService>("com.example.my-service")
 *
 * // Register in one plugin:
 * scope.services.register(MY_SERVICE, MyServiceImpl())
 *
 * // Retrieve in another plugin:
 * val svc = scope.services.get(MY_SERVICE)
 * ```
 *
 * @param T the service type
 * @param id a unique identifier (reverse-domain style recommended)
 */
class ServiceKey<T : Any>(val id: String) {
    override fun equals(other: Any?): Boolean = other is ServiceKey<*> && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "ServiceKey($id)"
}
