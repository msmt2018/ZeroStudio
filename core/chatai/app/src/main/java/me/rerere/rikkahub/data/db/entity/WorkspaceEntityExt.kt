package me.rerere.rikkahub.data.db.entity

import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.workspace.Workspace
import me.rerere.workspace.WorkspaceShellStatus

fun WorkspaceEntity.toolApprovalOverrides(): Map<String, Boolean> = runCatching {
    JsonInstant.decodeFromString<Map<String, Boolean>>(toolApprovals)
}.getOrDefault(emptyMap())

fun WorkspaceEntity.toWorkspace(): Workspace = Workspace(
    id = id,
    name = name,
    root = root,
    shellStatus = runCatching { WorkspaceShellStatus.valueOf(shellStatus) }
        .getOrDefault(WorkspaceShellStatus.DISABLED),
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastAccessAt = lastAccessAt,
)
