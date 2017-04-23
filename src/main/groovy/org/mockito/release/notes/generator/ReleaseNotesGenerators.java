package org.mockito.release.notes.generator;

import org.mockito.release.exec.Exec;
import org.mockito.release.exec.ProcessRunner;
import org.mockito.release.notes.contributors.Contributors;
import org.mockito.release.notes.contributors.GitHubContributorsProvider;
import org.mockito.release.notes.improvements.Improvements;
import org.mockito.release.notes.improvements.ImprovementsProvider;
import org.mockito.release.notes.vcs.ContributionsProvider;
import org.mockito.release.notes.vcs.ReleasedVersionsProvider;
import org.mockito.release.notes.vcs.Vcs;

import java.io.File;

public class ReleaseNotesGenerators {

    //TODO move entire "org.mockito.release.notes" -> "org.mockito.release.internal.notes"

    public static ReleaseNotesGenerator releaseNotesGenerator(File workDir, String repository, String readOnlyAuthToken) {
        ProcessRunner processRunner = Exec.getProcessRunner(workDir);
        ContributionsProvider contributionsProvider = Vcs.getContributionsProvider(processRunner);
        ImprovementsProvider improvementsProvider = Improvements.getGitHubProvider(repository, readOnlyAuthToken);
        ReleasedVersionsProvider releasedVersionsProvider = Vcs.getReleaseDateProvider(processRunner);
        GitHubContributorsProvider contributorsProvider = Contributors.getGitHubContibutorsProvider(repository, readOnlyAuthToken);
        return new DefaultReleaseNotesGenerator(contributionsProvider, improvementsProvider, releasedVersionsProvider,
                contributorsProvider);
    }
}
