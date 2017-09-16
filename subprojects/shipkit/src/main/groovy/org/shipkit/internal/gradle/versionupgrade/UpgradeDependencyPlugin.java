package org.shipkit.internal.gradle.versionupgrade;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.shipkit.gradle.configuration.ShipkitConfiguration;
import org.shipkit.gradle.exec.ShipkitExecTask;
import org.shipkit.gradle.git.GitPushTask;
import org.shipkit.internal.gradle.configuration.DeferredConfiguration;
import org.shipkit.internal.gradle.configuration.ShipkitConfigurationPlugin;
import org.shipkit.internal.gradle.git.GitAuthPlugin;
import org.shipkit.internal.gradle.git.tasks.GitCheckOutTask;
import org.shipkit.internal.gradle.git.tasks.GitPullTask;
import org.shipkit.internal.gradle.util.TaskMaker;
import org.shipkit.internal.util.IncubatingWarning;

import static org.shipkit.internal.gradle.exec.ExecCommandFactory.execCommand;

/**
 * BEWARE! This plugin is in incubating state, so its API may change in the future!
 * The plugin applies following plugins:
 *
 * <ul>
 *     <li>{@link ShipkitConfigurationPlugin}</li>
 *     <li>{@link GitAuthPlugin}</li>
 * </ul>
 *
 * and adds following tasks:
 *
 * <ul>
 *     <li>checkoutVersionUpgradeBaseBranch - checkouts base branch - the branch to which version upgrade should be applied through pull request</li>
 *     <li>pullUpstream - syncs the fork on which we perform version upgrade with the upstream repo</li>
 *     <li>checkoutVersionUpgradeVersionBranch - checkouts version branch - a new branch where version will be upgraded</li>
 *     <li>replaceVersion - replaces version in build file, using dependency pattern</li>
 *     <li>commitVersionUpgrade - commits replaced version</li>
 *     <li>pushVersionUpgrade - pushes the commit to the version branch</li>
 *     <li>createPullRequest - creates a pull request between base and version branches</li>
 *     <li>performVersionUpgrade - task aggregating all of the above</li>
 * </ul>
 *
 * Plugin should be used in client projects that want to have automated version upgrades of some other dependency, that use the producer version of this plugin.
 * Project with the producer plugin applied would then clone a fork of client project and run './gradlew performVersionUpgrade -Pdependency=${group:name:version}' on it.
 *
 * Example of plugin usage:
 *
 * Configure your 'shipkit.gradle' file like here:
 *
 *      apply plugin: 'org.shipkit.upgrade-dependency'
 *
 *      upgradeDependency{
 *          baseBranch = 'release/2.x'
 *          buildFile = file('build.gradle')
 *      }
 *
 * and then call it:
 *
 * ./gradlew performVersionUpgrade -Pdependency=org.shipkit:shipkit:1.2.3
 *
 */
public class UpgradeDependencyPlugin implements Plugin<Project> {

    public static final String CHECKOUT_BASE_BRANCH = "checkoutBaseBranch";
    public static final String PULL_UPSTREAM = "pullUpstream";
    public static final String CHECKOUT_VERSION_BRANCH = "checkoutVersionBranch";
    public static final String REPLACE_VERSION = "replaceVersion";
    public static final String COMMIT_VERSION_UPGRADE = "commitVersionUpgrade";
    public static final String PUSH_VERSION_UPGRADE = "pushVersionUpgrade";
    public static final String CREATE_PULL_REQUEST = "createPullRequest";
    public static final String PERFORM_VERSION_UPGRADE = "performVersionUpgrade";

    public static final String DEPENDENCY_PROJECT_PROPERTY = "dependency";

    private UpgradeDependencyExtension upgradeDependencyExtension;

    @Override
    public void apply(final Project project) {
        IncubatingWarning.warn("upgrade-dependency plugin");
        final GitAuthPlugin gitAuthPlugin = project.getPlugins().apply(GitAuthPlugin.class);
        final ShipkitConfiguration conf = project.getPlugins().apply(ShipkitConfigurationPlugin.class).getConfiguration();

        upgradeDependencyExtension = project.getExtensions().create("upgradeDependency", UpgradeDependencyExtension.class);

        // set defaults
        upgradeDependencyExtension.setBuildFile(project.file("build.gradle"));
        upgradeDependencyExtension.setBaseBranch("master");

        String dependency = (String) project.findProperty(DEPENDENCY_PROJECT_PROPERTY);

        new DependencyNewVersionParser(dependency).fillVersionUpgradeExtension(upgradeDependencyExtension);

        TaskMaker.task(project, CHECKOUT_BASE_BRANCH, GitCheckOutTask.class, new Action<GitCheckOutTask>() {
            @Override
            public void execute(final GitCheckOutTask task) {
                task.setDescription("Checks out the base branch.");

                DeferredConfiguration.deferredConfiguration(project, new Runnable() {
                    @Override
                    public void run() {
                        task.setRev(upgradeDependencyExtension.getBaseBranch());
                    }
                });
            }
        });

        TaskMaker.task(project, PULL_UPSTREAM, GitPullTask.class, new Action<GitPullTask>() {
            @Override
            public void execute(final GitPullTask task) {
                task.setDescription("Performs git pull from upstream repository.");
                task.mustRunAfter(CHECKOUT_BASE_BRANCH);
                task.setDryRun(conf.isDryRun());

                DeferredConfiguration.deferredConfiguration(project, new Runnable() {
                    @Override
                    public void run() {
                        task.setRev(upgradeDependencyExtension.getBaseBranch());
                    }
                });

                gitAuthPlugin.provideAuthTo(task, new Action<GitAuthPlugin.GitAuth>() {
                    @Override
                    public void execute(GitAuthPlugin.GitAuth gitAuth) {
                        task.setSecretValue(gitAuth.getSecretValue());
                        task.setUrl(gitAuth.getRepositoryUrl());
                    }
                });
            }
        });

        TaskMaker.task(project, CHECKOUT_VERSION_BRANCH, GitCheckOutTask.class, new Action<GitCheckOutTask>() {
            public void execute(final GitCheckOutTask task) {
                task.setDescription("Creates a new version branch and checks it out.");
                task.mustRunAfter(PULL_UPSTREAM);
                task.setRev(getVersionBranchName(upgradeDependencyExtension));
                task.setNewBranch(true);
            }
        });

        final ReplaceVersionTask replaceVersionTask = TaskMaker.task(project, REPLACE_VERSION, ReplaceVersionTask.class, new Action<ReplaceVersionTask>() {
            @Override
            public void execute(final ReplaceVersionTask task) {
                task.setDescription("Replaces dependency version in build file.");
                task.mustRunAfter(CHECKOUT_VERSION_BRANCH);
                task.setVersionUpgrade(upgradeDependencyExtension);
            }
        });

        TaskMaker.task(project, COMMIT_VERSION_UPGRADE, ShipkitExecTask.class, new Action<ShipkitExecTask>() {
            @Override
            public void execute(final ShipkitExecTask exec) {
                exec.setDescription("Commits updated build file.");
                exec.mustRunAfter(REPLACE_VERSION);

                DeferredConfiguration.deferredConfiguration(project, new Runnable() {
                    @Override
                    public void run() {
                        String message = String.format("%s version upgraded to %s", upgradeDependencyExtension.getDependencyName(), upgradeDependencyExtension.getNewVersion());
                        exec.execCommand(execCommand("Committing build file",
                            "git", "commit", "-m", message, upgradeDependencyExtension.getBuildFile().getAbsolutePath()));
                    }
                });
                exec.onlyIf(wasBuildFileUpdatedSpec(replaceVersionTask));
            }
        });

        TaskMaker.task(project, PUSH_VERSION_UPGRADE, GitPushTask.class, new Action<GitPushTask>() {
            @Override
            public void execute(final GitPushTask task) {
                task.setDescription("Pushes updated config file to an update branch.");
                task.mustRunAfter(COMMIT_VERSION_UPGRADE);

                task.setDryRun(conf.isDryRun());
                task.getTargets().add(getVersionBranchName(upgradeDependencyExtension));

                gitAuthPlugin.provideAuthTo(task, new Action<GitAuthPlugin.GitAuth>() {
                    @Override
                    public void execute(GitAuthPlugin.GitAuth gitAuth) {
                        task.setSecretValue(gitAuth.getSecretValue());
                        task.setUrl(gitAuth.getRepositoryUrl());
                    }
                });

                task.onlyIf(wasBuildFileUpdatedSpec(replaceVersionTask));
            }
        });

        TaskMaker.task(project, CREATE_PULL_REQUEST, CreatePullRequestTask.class, new Action<CreatePullRequestTask>() {
            @Override
            public void execute(final CreatePullRequestTask task) {
                task.setDescription("Creates a pull request from branch with version upgraded to master");
                task.mustRunAfter(PUSH_VERSION_UPGRADE);
                task.setGitHubApiUrl(conf.getGitHub().getApiUrl());
                task.setDryRun(conf.isDryRun());
                task.setAuthToken(conf.getLenient().getGitHub().getWriteAuthToken());
                task.setVersionBranch(getVersionBranchName(upgradeDependencyExtension));
                task.setVersionUpgrade(upgradeDependencyExtension);

                gitAuthPlugin.provideAuthTo(task, new Action<GitAuthPlugin.GitAuth>() {
                    @Override
                    public void execute(GitAuthPlugin.GitAuth gitAuth) {
                        task.setForkRepositoryName(gitAuth.getRepositoryName());
                        String repoName = gitAuth.getRepositoryName().split("/")[1];

                        /*
                        TODO SF We need to change creation of pull requests because they require shipkit.gitHub.repository to be set.
                        Let's create ticket for this.
                        I am currently working on making this configuration setting optional.
                        I need this for bootstrap scenarios and it will make the configuration simpler, too.

                        Most likely, we will implement it by passing and additional parameter to version upgrade plugin

                        Below is a temporary hack
                        */
                        task.setUpstreamRepositoryName("mockito/" + repoName);
                    }
                });

                task.onlyIf(wasBuildFileUpdatedSpec(replaceVersionTask));
            }
        });

        TaskMaker.task(project, PERFORM_VERSION_UPGRADE, new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.setDescription("Checkouts new version branch, updates Shipkit dependency in config file, commits and pushes.");
                task.dependsOn(CHECKOUT_BASE_BRANCH);
                task.dependsOn(PULL_UPSTREAM);
                task.dependsOn(CHECKOUT_VERSION_BRANCH);
                task.dependsOn(REPLACE_VERSION);
                task.dependsOn(COMMIT_VERSION_UPGRADE);
                task.dependsOn(PUSH_VERSION_UPGRADE);
                task.dependsOn(CREATE_PULL_REQUEST);
            }
        });
    }

    private Spec<Task> wasBuildFileUpdatedSpec(final ReplaceVersionTask replaceVersionTask) {
        return new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return replaceVersionTask.isBuildFileUpdated();
            }
        };
    }

    private String getVersionBranchName(UpgradeDependencyExtension versionUpgrade) {
        return "upgrade-" + versionUpgrade.getDependencyName() + "-to-" + versionUpgrade.getNewVersion();
    }

    public UpgradeDependencyExtension getUpgradeDependencyExtension() {
        return upgradeDependencyExtension;
    }

}
