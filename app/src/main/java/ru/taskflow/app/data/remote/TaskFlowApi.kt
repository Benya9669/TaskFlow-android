package ru.taskflow.app.data.remote

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface TaskFlowApi {
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): AuthResponse
    @POST("auth/register") suspend fun register(@Body request: RegisterRequest): RegistrationResponse
    @POST("auth/verify-email") suspend fun verifyEmail(@Body request: VerifyEmailRequest): AuthResponse
    @POST("auth/refresh") suspend fun refresh(@Body request: RefreshRequest): RefreshResponse
    @GET("sync") suspend fun sync(
        @Query("since") since: String,
        @Query("cursor") cursor: String? = null,
        @Query("snapshot") snapshot: String? = null,
        @Query("limit") limit: Int = 500,
    ): SyncResponse
    @POST("sync/mutations") suspend fun sendMutations(@Body request: MutationBatch): MutationBatchResponse
    @GET("projects") suspend fun projects(@Query("include_archived") includeArchived: Boolean = true): ProjectListResponse
    @POST("projects") suspend fun createProject(@Body request: ProjectWriteRequest): ProjectResponse
    @PATCH("projects/{projectId}") suspend fun updateProject(@Path("projectId") projectId: String, @Body request: ProjectUpdateRequest): ProjectResponse
    @POST("projects/{projectId}/archive") suspend fun archiveProject(@Path("projectId") projectId: String, @Body request: VersionGuardRequest): ProjectResponse
    @HTTP(method = "DELETE", path = "projects/{projectId}/archive", hasBody = true)
    suspend fun restoreProject(@Path("projectId") projectId: String, @Body request: VersionGuardRequest): ProjectResponse
    @GET("kanban/columns") suspend fun kanbanColumns(): KanbanColumnListResponse
    @POST("kanban/columns") suspend fun createKanbanColumn(@Body request: KanbanColumnWriteRequest): KanbanColumnResponse
    @PATCH("kanban/columns/{columnId}") suspend fun updateKanbanColumn(@Path("columnId") columnId: String, @Body request: KanbanColumnUpdateRequest): KanbanColumnResponse
    @POST("kanban/columns/reorder") suspend fun reorderKanbanColumns(@Body request: KanbanColumnOrderRequest): KanbanColumnListResponse
    @HTTP(method = "DELETE", path = "kanban/columns/{columnId}", hasBody = true)
    suspend fun deleteKanbanColumn(@Path("columnId") columnId: String, @Body request: KanbanColumnDeleteRequest): DeletedKanbanColumnResponse
    @GET("me") suspend fun me(): UserResponse
    @PATCH("account") suspend fun updateAccount(@Body request: AccountUpdateRequest): AccountUpdateResponse
}

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, @Json(name = "display_name") val displayName: String)
data class VerifyEmailRequest(val token: String)
data class RegistrationResponse(@Json(name = "verification_required") val verificationRequired: Boolean, val email: String)
data class RefreshRequest(@Json(name = "refresh_token") val refreshToken: String)
data class AuthResponse(val token: String, @Json(name = "refresh_token") val refreshToken: String, val user: UserDto)
data class RefreshResponse(val token: String, @Json(name = "refresh_token") val refreshToken: String, @Json(name = "session_id") val sessionId: String)
data class UserDto(val id: String, val email: String, @Json(name = "display_name") val displayName: String, val timezone: String = "UTC")
data class UserResponse(val user: UserDto)
data class AccountUpdateRequest(@Json(name = "display_name") val displayName: String? = null, val timezone: String? = null)
data class AccountUpdateResponse(val user: UserDto, @Json(name = "email_change_pending") val emailChangePending: String? = null)

data class TaskDto(
    val id: String, @Json(name = "owner_id") val ownerId: String, @Json(name = "project_id") val projectId: String?,
    @Json(name = "column_id") val columnId: String, val title: String, val description: String = "", val status: String,
    val priority: String, @Json(name = "scheduled_date") val scheduledDate: String?, @Json(name = "due_at") val dueAt: String?,
    @Json(name = "estimated_minutes") val estimatedMinutes: Int?, @Json(name = "kanban_position") val kanbanPosition: Int,
    val recurrence: String?, @Json(name = "reminder_offsets") val reminderOffsets: List<Int> = emptyList(), val tags: List<String> = emptyList(),
    @Json(name = "created_at") val createdAt: String, @Json(name = "updated_at") val updatedAt: String,
    val version: Int, @Json(name = "deleted_at") val deletedAt: String?,
)
data class ProjectDto(val id: String, @Json(name = "owner_id") val ownerId: String, val name: String, val color: String, @Json(name = "created_at") val createdAt: String, @Json(name = "updated_at") val updatedAt: String, val version: Int, @Json(name = "deleted_at") val deletedAt: String?, @Json(name = "archived_at") val archivedAt: String?)
data class ProjectWriteRequest(val name: String, val color: String)
data class ProjectUpdateRequest(val name: String, val color: String, @Json(name = "expected_version") val expectedVersion: Int)
data class VersionGuardRequest(@Json(name = "expected_version") val expectedVersion: Int)
data class ProjectResponse(val project: ProjectDto)
data class ProjectListResponse(val projects: List<ProjectDto>)
data class KanbanColumnDto(val id: String, @Json(name = "owner_id") val ownerId: String, val name: String, val color: String, @Json(name = "semantic_status") val semanticStatus: String, val position: Int, @Json(name = "created_at") val createdAt: String, @Json(name = "updated_at") val updatedAt: String, val version: Int, @Json(name = "deleted_at") val deletedAt: String?)
data class KanbanColumnWriteRequest(val name: String, val color: String, @Json(name = "semantic_status") val semanticStatus: String)
data class KanbanColumnUpdateRequest(val name: String, val color: String, @Json(name = "semantic_status") val semanticStatus: String, @Json(name = "expected_version") val expectedVersion: Int)
data class KanbanColumnOrderRequest(@Json(name = "column_ids") val columnIds: List<String>)
data class KanbanColumnDeleteRequest(@Json(name = "move_to_column_id") val moveToColumnId: String, @Json(name = "expected_version") val expectedVersion: Int)
data class KanbanColumnResponse(val column: KanbanColumnDto)
data class KanbanColumnListResponse(val columns: List<KanbanColumnDto>)
data class DeletedKanbanColumnResponse(val deleted: Boolean, val id: String, @Json(name = "moved_to_column_id") val movedToColumnId: String)
data class SyncResponse(val snapshot: String, val cursor: String, @Json(name = "has_more") val hasMore: Boolean, @Json(name = "next_cursor") val nextCursor: String?, val tasks: List<TaskDto>, val projects: List<ProjectDto>, @Json(name = "kanban_columns") val kanbanColumns: List<KanbanColumnDto>)
data class MutationBatch(val mutations: List<MutationDto>)
data class MutationDto(
    val id: String,
    val operation: String,
    @Json(name = "task_id") val taskId: String?,
    val body: Map<String, Any?>? = null,
    val entity: String = "task",
    @Json(name = "project_id") val projectId: String? = null,
)
data class MutationBatchResponse(val mutations: List<MutationResultDto>)
data class MutationResultDto(val id: String, val status: Int, val response: Map<String, Any?>)
