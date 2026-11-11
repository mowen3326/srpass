package app.srpass.kykfz01.crypto

/**
 * In-memory [KeyEnvelopeStore] for unit tests. Mirrors the production
 * SharedPreferences store closely enough for the crypto round-trips.
 */
class InMemoryKeyEnvelopeStore : KeyEnvelopeStore {

    private val map = mutableMapOf<String, KeyEnvelope>()

    override fun save(envelope: KeyEnvelope) {
        map[envelope.channel.id] = envelope
    }

    override fun load(channel: Channel): KeyEnvelope? = map[channel.id]

    override fun clear(channel: Channel) {
        map.remove(channel.id)
    }

    override fun hasAny(): Boolean = map.isNotEmpty()
}
