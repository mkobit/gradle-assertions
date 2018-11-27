package com.mkobit.gradle.test.strikt.testkit.runner

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.TaskOutcome
import strikt.api.Assertion

/**
 * Maps an assertion to the task at the provided [taskPath].
 * @see BuildResult.task
 */
fun <T : BuildResult> Assertion.Builder<T>.task(taskPath: String): Assertion.Builder<BuildTask?> =
  get("task path") { task(taskPath) }

/**
 * Maps an assertion to the task paths of the build with the provided [outcome].
 * @see BuildResult.taskPaths
 */
fun <T : BuildResult> Assertion.Builder<T>.taskPaths(outcome: TaskOutcome): Assertion.Builder<List<String>> =
  get("task paths with outcome $outcome") { taskPaths(outcome) }

/**
 * Maps an assertion to an assertion on all of the tasks from the build.
 * @see BuildResult.getTasks
 */
fun <T : BuildResult> Assertion.Builder<T>.tasks(): Assertion.Builder<List<BuildTask>> = get("all tasks", BuildResult::getTasks)

/**
 * Maps an assertion to the tasks of the build with the provided [outcome].
 * @see BuildResult
 * @see TaskOutcome
 */
fun <T : BuildResult> Assertion.Builder<T>.tasks(outcome: TaskOutcome): Assertion.Builder<List<BuildTask>> =
  get("tasks with outcome $outcome") { tasks(outcome) }

/**
 * Maps an assertion to an assertion on the output.
 * @see BuildResult
 */
val <T : BuildResult> Assertion.Builder<T>.output: Assertion.Builder<String>
  get() = get("output", org.gradle.testkit.runner.BuildResult::getOutput)
