package org.mockito.release.internal.gradle;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Exec;
import org.mockito.release.gradle.ReleaseConfiguration;
import org.mockito.release.gradle.SecureExecTask;
import org.mockito.release.internal.gradle.util.GitUtil;
import org.mockito.release.internal.gradle.util.TaskMaker;

import static org.mockito.release.internal.gradle.configuration.DeferredConfiguration.deferredConfiguration;
import static org.mockito.release.internal.gradle.configuration.LazyConfiguration.lazyConfiguration;
import static org.mockito.release.internal.gradle.util.GitUtil.getTag;
import static org.mockito.release.internal.gradle.util.StringUtil.join;

/**
 * Adds Git-specific tasks needed for the release process.
 */
public class GitPlugin implements Plugin<Project> {

    private static final Logger LOG = Logging.getLogger(GitPlugin.class);

    static final String COMMIT_TASK = "gitCommit";
    static final String TAG_TASK = "gitTag";
    static final String PUSH_TASK = "gitPush";
    static final String COMMIT_CLEANUP_TASK = "gitCommitCleanUp";
    static final String TAG_CLEANUP_TASK = "gitTagCleanUp";
    static final String UNSHALLOW_TASK = "gitUnshallow";
    static final String CHECKOUT_BRANCH_TASK = "checkOutBranch";
    static final String SET_USER_TASK = "setGitUserName";
    static final String SET_EMAIL_TASK = "setGitUserEmail";

    public void apply(final Project project) {
        final ReleaseConfiguration conf = project.getPlugins().apply(ReleaseConfigurationPlugin.class).getConfiguration();
        final GitStatusPlugin.GitStatus gitStatus = project.getPlugins().apply(GitStatusPlugin.class).getGitStatus();

        TaskMaker.execTask(project, COMMIT_TASK, new Action<Exec>() {
            public void execute(final Exec t) {
                t.setDescription("Commits staged changes using generic --author");
                deferredConfiguration(project, new Runnable() {
                    @Override
                    public void run() {
                        t.commandLine("git", "commit", "--author",
                                GitUtil.getGitGenericUserNotation(conf), "-m",
                                GitUtil.getCommitMessage(conf, "Bumped version and updated release notes"));
                    }
                });
            }
        });

        TaskMaker.execTask(project, TAG_TASK, new Action<Exec>() {
            public void execute(final Exec t) {
                t.mustRunAfter(COMMIT_TASK);
                final String tag = GitUtil.getTag(conf, project);
                t.setDescription("Creates new version tag '" + tag + "'");
                deferredConfiguration(project, new Runnable() {
                    @Override
                    public void run() {
                        t.commandLine("git", "tag", "-a", tag, "-m",
                        GitUtil.getCommitMessage(conf, "Created new tag " + tag));
                    }
                });
            }
        });

        TaskMaker.task(project, PUSH_TASK, SecureExecTask.class, new Action<SecureExecTask>() {
            public void execute(final SecureExecTask t) {
                t.setDescription("Pushes changes to remote repo.");
                t.mustRunAfter(COMMIT_TASK, TAG_TASK);

                lazyConfiguration(t, new Runnable() {
                    public void run() {
                        t.setCommandLine(GitUtil.getGitPushArgs(conf, project, gitStatus.getBranch()));
                        t.setSecretValue(conf.getGitHub().getWriteAuthToken());
                    }
                });
            }
        });

        TaskMaker.execTask(project, COMMIT_CLEANUP_TASK, new Action<Exec>() {
            public void execute(final Exec t) {
                t.setDescription("Removes last commit, using 'reset --hard HEAD~'");
                //TODO replace with combination of 'git reset --soft HEAD~ && git stash' so that we don't lose commits
                t.commandLine("git", "reset", "--hard", "HEAD~");
            }
        });

        TaskMaker.execTask(project, TAG_CLEANUP_TASK, new Action<Exec>() {
            public void execute(final Exec t) {
                t.setDescription("Deletes version tag '" + getTag(conf, project) + "'");
                t.commandLine("git", "tag", "-d", getTag(conf, project));
            }
        });

        TaskMaker.execTask(project, UNSHALLOW_TASK, new Action<Exec>() {
            public void execute(final Exec t) {
                //Travis default clone is shallow which will prevent correct release notes generation for repos with lots of commits
                t.commandLine("git", "fetch", "--unshallow");
                t.setDescription("Ensures good chunk of recent commits is available for release notes automation. Runs: " + t.getCommandLine());

                t.setIgnoreExitValue(true);
                t.doLast(new Action<Task>() {
                    public void execute(Task task) {
                        if (t.getExecResult().getExitValue() != 0) {
                            LOG.lifecycle("  Following git command failed and will be ignored:" +
                                    "\n    " + join(t.getCommandLine(), " ") +
                                    "\n  Most likely the repository already contains all history.");
                        }
                    }
                });
            }
        });

        TaskMaker.execTask(project, CHECKOUT_BRANCH_TASK, new Action<Exec>() {
            public void execute(final Exec t) {
                t.setDescription("Checks out the branch that can be committed. CI systems often check out revision that is not committable.");
                lazyConfiguration(t, new Runnable() {
                    public void run() {
                        t.commandLine("git", "checkout", gitStatus.getBranch());
                    }
                });
            }
        });

        TaskMaker.execTask(project, SET_USER_TASK, new Action<Exec>() {
            public void execute(final Exec t) {
                t.setDescription("Overwrites local git 'user.name' with a generic name. Intended for CI.");
                //TODO replace all doFirst in this class with LazyConfiguration
                t.doFirst(new Action<Task>() {
                    public void execute(Task task) {
                        //using doFirst() so that we request and validate presence of env var only during execution time
                        t.commandLine("git", "config", "--local", "user.name", conf.getGit().getUser());
                    }
                });
            }
        });

        TaskMaker.execTask(project, SET_EMAIL_TASK, new Action<Exec>() {
            public void execute(final Exec t) {
                t.setDescription("Overwrites local git 'user.email' with a generic email. Intended for CI.");
                t.doFirst(new Action<Task>() {
                    public void execute(Task task) {
                        //using doFirst() so that we request and validate presence of env var only during execution time
                        //TODO consider adding 'lazyExec' task or method that automatically uses do first
                        t.commandLine("git", "config", "--local", "user.email", conf.getGit().getEmail());
                    }
                });
            }
        });
    }

}
