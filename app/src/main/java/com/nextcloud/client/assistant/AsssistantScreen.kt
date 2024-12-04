/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Alper Ozturk <alper.ozturk@nextcloud.com>
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.nextcloud.client.assistant

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.client.assistant.component.AddTaskAlertDialog
import com.nextcloud.client.assistant.component.CenterText
import com.nextcloud.client.assistant.extensions.getInputTitle
import com.nextcloud.client.assistant.model.ScreenOverlayState
import com.nextcloud.client.assistant.model.ScreenState
import com.nextcloud.client.assistant.repository.AssistantMockRepository
import com.nextcloud.client.assistant.task.TaskView
import com.nextcloud.client.assistant.taskTypes.TaskTypesRow
import com.nextcloud.ui.composeActivity.ComposeActivity
import com.nextcloud.ui.composeComponents.alertDialog.SimpleAlertDialog
import com.nextcloud.ui.composeComponents.bottomSheet.MoreActionsBottomSheet
import com.owncloud.android.R
import com.owncloud.android.lib.resources.assistant.model.Task
import com.owncloud.android.lib.resources.assistant.model.TaskTypeData
import com.owncloud.android.utils.DisplayUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: AssistantViewModel, activity: Activity) {
    val messageId by viewModel.snackbarMessageId.collectAsState()
    val screenOverlayState by viewModel.screenOverlayState.collectAsState()

    val selectedTaskType by viewModel.selectedTaskType.collectAsState()
    val filteredTaskList by viewModel.filteredTaskList.collectAsState()
    val screenState by viewModel.screenState.collectAsState()
    val taskTypes by viewModel.taskTypes.collectAsState()
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullToRefreshState()

    @Suppress("MagicNumber")
    Box(
        modifier = Modifier.pullToRefresh(
            screenState == ScreenState.Refreshing,
            pullRefreshState,
            onRefresh = {
                scope.launch {
                    delay(1500)
                    viewModel.fetchTaskList()
                }
            }
        )
    ) {
        ShowScreenState(screenState, selectedTaskType, taskTypes, viewModel, filteredTaskList)

        ShowLinearProgressIndicator(screenState, pullRefreshState)

        AddFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            selectedTaskType,
            viewModel
        )
    }

    showSnackBarMessage(messageId, activity, viewModel)
    ShowOverlayState(screenOverlayState, activity, viewModel)
}

@Composable
private fun ShowScreenState(
    screenState: ScreenState?,
    selectedTaskType: TaskTypeData?,
    taskTypes: List<TaskTypeData>?,
    viewModel: AssistantViewModel,
    filteredTaskList: List<Task>?
) {
    when (screenState) {
        ScreenState.Refreshing -> {
            CenterText(text = stringResource(id = R.string.assistant_screen_loading))
        }

        ScreenState.EmptyContent -> {
            EmptyTaskList(selectedTaskType, taskTypes, viewModel)
        }

        ScreenState.Content -> {
            AssistantContent(
                filteredTaskList ?: listOf(),
                taskTypes,
                selectedTaskType,
                viewModel
            )
        }

        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowLinearProgressIndicator(screenState: ScreenState?, pullToRefreshState: PullToRefreshState) {
    if (screenState == ScreenState.Refreshing) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { pullToRefreshState.distanceFraction },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AddFloatingActionButton(
    modifier: Modifier,
    selectedTaskType: TaskTypeData?,
    viewModel: AssistantViewModel
) {
    FloatingActionButton(
        modifier = modifier,
        onClick = {
            selectedTaskType?.let {
                val newState = ScreenOverlayState.AddTask(it, "")
                viewModel.updateScreenState(newState)
            }
        }
    ) {
        Icon(Icons.Filled.Add, "Add Task Icon")
    }
}

private fun showSnackBarMessage(messageId: Int?, activity: Activity, viewModel: AssistantViewModel) {
    messageId?.let {
        DisplayUtils.showSnackMessage(
            activity,
            activity.getString(it)
        )

        viewModel.updateSnackbarMessage(null)
    }
}

@Suppress("LongMethod")
@Composable
private fun ShowOverlayState(state: ScreenOverlayState?, activity: Activity, viewModel: AssistantViewModel) {
    when (state) {
        is ScreenOverlayState.AddTask -> {
            AddTaskAlertDialog(
                title = state.taskType.name,
                description = state.taskType.description,
                defaultInput = state.input,
                addTask = { input ->
                    state.taskType.let { taskType ->
                        viewModel.createTask(input = input, taskType = taskType)
                    }
                },
                dismiss = {
                    viewModel.updateScreenState(null)
                }
            )
        }

        is ScreenOverlayState.DeleteTask -> {
            SimpleAlertDialog(
                title = stringResource(id = R.string.assistant_screen_delete_task_alert_dialog_title),
                description = stringResource(id = R.string.assistant_screen_delete_task_alert_dialog_description),
                dismiss = { viewModel.updateScreenState(null) },
                onComplete = { viewModel.deleteTask(state.id) }
            )
        }

        is ScreenOverlayState.TaskActions -> {
            val actions = state.getActions(activity, onEditCompleted = { addTask ->
                viewModel.updateScreenState(addTask)
            }, onDeleteCompleted = { deleteTask ->
                viewModel.updateScreenState(deleteTask)
            })

            MoreActionsBottomSheet(
                title = state.task.getInputTitle(),
                actions = actions,
                dismiss = { viewModel.updateScreenState(null) }
            )
        }

        else -> Unit
    }
}

@Composable
private fun AssistantContent(
    taskList: List<Task>,
    taskTypes: List<TaskTypeData>?,
    selectedTaskType: TaskTypeData?,
    viewModel: AssistantViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        taskTypes?.let {
            TaskTypesRow(selectedTaskType, data = taskTypes) { task ->
                viewModel.selectTaskType(task)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            items(taskList) { task ->
                TaskView(
                    task,
                    showTaskActions = {
                        val newState = ScreenOverlayState.TaskActions(task)
                        viewModel.updateScreenState(newState)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EmptyTaskList(
    selectedTaskType: TaskTypeData?,
    taskTypes: List<TaskTypeData>?,
    viewModel: AssistantViewModel
) {
    val text = stringResource(
        id = R.string.assistant_screen_no_task_available_text,
        selectedTaskType?.name ?: ""
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        taskTypes?.let {
            TaskTypesRow(selectedTaskType, data = taskTypes) { task ->
                viewModel.selectTaskType(task)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        CenterText(text = text)
    }
}

@Composable
@Preview
private fun AssistantScreenPreview() {
    val mockRepository = AssistantMockRepository()
    MaterialTheme(
        content = {
            AssistantScreen(
                viewModel = AssistantViewModel(repository = mockRepository),
                activity = ComposeActivity()
            )
        }
    )
}

@Composable
@Preview
private fun AssistantEmptyScreenPreview() {
    val mockRepository = AssistantMockRepository(giveEmptyTasks = true)
    MaterialTheme(
        content = {
            AssistantScreen(
                viewModel = AssistantViewModel(repository = mockRepository),
                activity = ComposeActivity()
            )
        }
    )
}
