package com.sergeymild.gradle.slack

import com.sergeymild.gradle.slack.model.SlackMessageTransformer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.logging.StandardOutputListener
import org.gradle.api.tasks.TaskState
import slack.SlackApi
import slack.SlackMessage

/**
 * Created by user on 19/08/16.
 */
class SlackPlugin implements Plugin<Project> {
    SlackPluginExtension mExtension
    StringBuilder mTaskLogBuilder

    void apply(Project project) {

        mTaskLogBuilder = new StringBuilder()
        mExtension = project.extensions.create('slack', SlackPluginExtension)

        project.afterEvaluate {
            if (mExtension.url != null && mExtension.enabled)
                monitorTasksLifeCycle(project)
        }
    }

    void monitorTasksLifeCycle(Project project) {

        project.getGradle().getTaskGraph().addTaskExecutionListener(new TaskExecutionListener() {
            void beforeExecute(Task task) {
                task.logging.addStandardOutputListener(new StandardOutputListener() {
                    void onOutput(CharSequence charSequence) {
                        mTaskLogBuilder.append(charSequence)
                    }
                })
            }

            @Override
            void afterExecute(Task task, TaskState state) {
                handleTaskFinished(task, state)
            }
        })
    }

    void handleTaskFinished(Task task, TaskState state) {
        Throwable failure = state.getFailure()
        boolean shouldSendMessage = failure != null || shouldMonitorTask(task);

        // only send a slack message if the task failed
        // or the task is registered to be monitored
        if (shouldSendMessage) {
            SlackMessage slackMessage = SlackMessageTransformer.buildSlackMessage(mExtension.title, task, state, mTaskLogBuilder.toString())
            SlackApi api = new SlackApi(mExtension.url)
            api.call(slackMessage)
        }
    }

    boolean shouldMonitorTask(Task task) {
        for (dependentTask in mExtension.dependsOnTasks) {
            if (task.getName().equals(dependentTask)) {
                return true
            }
        }
        return false
    }
}
