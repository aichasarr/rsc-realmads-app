package com.mongodb.app.data
import com.mongodb.app.domain.PriorityLevel


import com.mongodb.app.app
import com.mongodb.app.domain.RSCItem
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.SyncException
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Repository for accessing Realm Sync.
 */
interface SyncRepository {

    /**
     * Returns a flow with the tasks for the current subscription.
     */
    fun getTaskList(): Flow<ResultsChange<RSCItem>>

    /**
     * Update the `isComplete` flag for a specific [RSCItem].
     */
    suspend fun toggleIsComplete(task: RSCItem)

    /**
     * Adds a task that belongs to the current user using the specified [taskSummary].
     */
    suspend fun addTask(taskSummary: String, taskPriority: PriorityLevel)

    /**
     * Updates the Sync subscriptions based on the specified [SubscriptionType].
     */
    suspend fun updateSubscriptions(subscriptionType: SubscriptionType)

    /**
     * Deletes a given task.
     */
    suspend fun deleteTask(task: RSCItem)

    /**
     * Returns the active [SubscriptionType].
     */
    fun getActiveSubscriptionType(realm: Realm? = null): SubscriptionType

    /**
     * Pauses synchronization with MongoDB. This is used to emulate a scenario of no connectivity.
     */
    fun pauseSync()

    /**
     * Resumes synchronization with MongoDB.
     */
    fun resumeSync()

    /**
     * Whether the given [task] belongs to the current user logged in to the app.
     */
    fun isTaskMine(task: RSCItem): Boolean

    /**
     * Closes the realm instance held by this repository.
     */
    fun close()
}

/**
 * Repo implementation used in runtime.
 */
class RealmSyncRepository(
    onSyncError: (session: SyncSession, error: SyncException) -> Unit
) : SyncRepository {

    private val realm: Realm
    private val config: SyncConfiguration
    private val currentUser: User
        get() = app.currentUser!!

    init {
        config = SyncConfiguration.Builder(currentUser, setOf(RSCItem::class))
            .initialSubscriptions(rerunOnOpen = true) { realm ->
                // Subscribe to the active subscriptionType - first time defaults to ALL tasks
                val activeSubscriptionType = getActiveSubscriptionType(realm)
                add(getQuery(realm, activeSubscriptionType), activeSubscriptionType.name, updateExisting = true)
            }
            .errorHandler { session: SyncSession, error: SyncException ->
                onSyncError.invoke(session, error)
            }
            .waitForInitialRemoteData()
            .build()

        realm = Realm.open(config)

        // Mutable states must be updated on the UI thread
        CoroutineScope(Dispatchers.Main).launch {
            realm.subscriptions.waitForSynchronization()
        }
    }

    override fun getTaskList(): Flow<ResultsChange<RSCItem>> {
        return realm.query<RSCItem>()
            .sort(Pair("_id", Sort.ASCENDING))
            .asFlow()
    }

    override suspend fun toggleIsComplete(task: RSCItem) {
        realm.write {
            val latestVersion = findLatest(task)
            latestVersion!!.isComplete = !latestVersion.isComplete
        }
    }

    override suspend fun addTask(taskSummary: String, taskPriority: PriorityLevel) {
        val task = RSCItem().apply {
            owner_id = currentUser.id
            summary = taskSummary
            priority = taskPriority.ordinal

        }
        realm.write {
            copyToRealm(task)
        }
    }

    override suspend fun updateSubscriptions(subscriptionType: SubscriptionType) {
        realm.subscriptions.update {
            removeAll()
            val query = when (subscriptionType) {
                SubscriptionType.ALL -> getQuery(realm, SubscriptionType.ALL)
                SubscriptionType.FILTERED -> getQuery(realm, SubscriptionType.FILTERED)

            }
            add(query, subscriptionType.name)
        }
    }

    override suspend fun deleteTask(task: RSCItem) {
        realm.write {
            delete(findLatest(task)!!)
        }
        realm.subscriptions.waitForSynchronization(10.seconds)
    }

    override fun getActiveSubscriptionType(realm: Realm?): SubscriptionType {
        val realmInstance = realm ?: this.realm
        val subscriptions = realmInstance.subscriptions
        val firstOrNull = subscriptions.firstOrNull()
        return when (val name = firstOrNull?.name) {
            null,
            SubscriptionType.ALL.name -> SubscriptionType.ALL
            SubscriptionType.FILTERED.name -> SubscriptionType.FILTERED
            else -> throw IllegalArgumentException("Invalid Realm Sync subscription: '$name'")
        }
    }

    override fun pauseSync() {
        realm.syncSession.pause()
    }

    override fun resumeSync() {
        realm.syncSession.resume()
    }

    override fun isTaskMine(task: RSCItem): Boolean = task.owner_id == currentUser.id

    override fun close() = realm.close()

    private fun getQuery(realm: Realm, subscriptionType: SubscriptionType): RealmQuery<RSCItem> =
        when (subscriptionType) {
            SubscriptionType.ALL -> realm.query("owner_id == $0", currentUser.id)
            SubscriptionType.FILTERED -> realm.query("owner_id == $0 AND priority <= ${PriorityLevel.High.ordinal}", currentUser.id)

        }
}

/**
 * Mock repo for generating the Compose layout preview.
 */
class MockRepository : SyncRepository {
    override fun getTaskList(): Flow<ResultsChange<RSCItem>> = flowOf()
    override suspend fun toggleIsComplete(task: RSCItem) = Unit
    override suspend fun addTask(taskSummary: String, taskPriority: PriorityLevel) = Unit
    override suspend fun updateSubscriptions(subscriptionType: SubscriptionType) = Unit
    override suspend fun deleteTask(task: RSCItem) = Unit
    override fun getActiveSubscriptionType(realm: Realm?): SubscriptionType = SubscriptionType.ALL
    override fun pauseSync() = Unit
    override fun resumeSync() = Unit
    override fun isTaskMine(task: RSCItem): Boolean = task.owner_id == MOCK_OWNER_ID_MINE
    override fun close() = Unit

    companion object {
        const val MOCK_OWNER_ID_MINE = "A"
        const val MOCK_OWNER_ID_FILTERED = "B"

        fun getMockTask(index: Int): RSCItem = RSCItem().apply {
            this.summary = "Task $index"

            // Make every third task complete in preview
            this.isComplete = index % 3 == 0

            // Make every other task mine in preview
            this.owner_id = when {
                index % 2 == 0 -> MOCK_OWNER_ID_MINE
                else -> MOCK_OWNER_ID_FILTERED
            }
        }
    }
}

/**
 * The two types of subscriptions according to task priorities.
 */
enum class SubscriptionType {
    FILTERED, ALL
}
