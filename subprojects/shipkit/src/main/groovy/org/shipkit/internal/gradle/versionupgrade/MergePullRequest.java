package org.shipkit.internal.gradle.versionupgrade;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.shipkit.gradle.configuration.ShipkitConfiguration;
import org.shipkit.internal.gradle.configuration.ShipkitConfigurationPlugin;
import org.shipkit.internal.gradle.git.domain.PullRequestStatus;
import org.shipkit.internal.gradle.util.BranchUtils;
import org.shipkit.internal.util.GitHubApi;
import org.shipkit.internal.util.GitHubStatusCheck;
import org.shipkit.internal.util.IncubatingWarning;
import org.shipkit.internal.util.IncubatingWarningAcknowledged;

import java.util.function.Predicate;

class MergePullRequest {

    private static final Logger LOG = Logging.getLogger(MergePullRequest.class);

    public void mergePullRequest(MergePullRequestTask task) {
        GitHubApi gitHubApi = new GitHubApi(task.getGitHubApiUrl(), task.getAuthToken());
        mergePullRequest(task, gitHubApi, new GitHubStatusCheck(task, gitHubApi));
    }

    public void mergePullRequest(MergePullRequestTask task, GitHubApi gitHubApi, GitHubStatusCheck gitHubStatusCheck) {
        if (task.isDryRun()) {
            LOG.lifecycle(" Skipping pull request merging due to dryRun = true");
            return;
        }

        String headBranch = BranchUtils.getHeadBranch(task.getForkRepositoryName(), task.getVersionBranch());

        final ShipkitConfiguration configuration = task.getProject().getPlugins()
            .apply(ShipkitConfigurationPlugin.class).getConfiguration();
        Predicate acknowledgedIncubatingWarningPredicate = new IncubatingWarningAcknowledged(configuration).negate();
        IncubatingWarning.warn("merge pull requests", acknowledgedIncubatingWarningPredicate);

        LOG.lifecycle("Waiting for status of a pull request in repository '{}' between base = '{}' and head = '{}'.", task.getUpstreamRepositoryName(), task.getBaseBranch(), headBranch);

        String url = task.getPullRequestUrl();
        String body = "{" +
            "  \"merge_method\": \"merge\"," +
            "  \"base\": \"" + task.getBaseBranch() + "\"" +
            "}";

        try {
            PullRequestStatus checkStatus = gitHubStatusCheck.checkStatusWithRetries();

            if (checkStatus == PullRequestStatus.TIMEOUT) {
                throw new RuntimeException("Too many retries while trying to merge " + url + ". Merge aborted");
            }

            if (checkStatus == PullRequestStatus.NO_CHECK_DEFINED) {
                LOG.error("No checks defined in pull request " + url + "! Merge aborted.");
                return;
            }

            LOG.lifecycle("All checks passed! Merging pull request in repository '{}' between base = '{}' and head = '{}'.", task.getUpstreamRepositoryName(), task.getBaseBranch(), headBranch);
            gitHubApi.put("/repos/" + task.getUpstreamRepositoryName() + "/pulls/" + task.getPullRequestNumber() + "/merge", body);
        } catch (Exception e) {
            throw new GradleException(String.format("Exception happen while trying to merge pull request. Merge aborted. Original issue: %s", e.getMessage()), e);
        }
    }
}
