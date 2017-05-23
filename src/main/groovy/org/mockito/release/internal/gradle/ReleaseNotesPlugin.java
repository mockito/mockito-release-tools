package org.mockito.release.internal.gradle;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.mockito.release.gradle.IncrementalReleaseNotes;
import org.mockito.release.gradle.ReleaseConfiguration;
import org.mockito.release.gradle.ReleaseNotesFetcherTask;
import org.mockito.release.internal.gradle.util.TaskMaker;
import org.mockito.release.version.VersionInfo;

import java.io.File;

import static org.mockito.release.internal.gradle.configuration.DeferredConfiguration.deferredConfiguration;

/**
 * The plugin adds following tasks:
 *
 * <ul>
 *     <li>fetchReleaseNotes - fetches release notes data, see {@link ReleaseNotesFetcherTask}</li>
 *     <li>updateReleaseNotes - updates release notes file in place, see {@link IncrementalReleaseNotes.UpdateTask}</li>
 *     <li>previewReleaseNotes - prints incremental release notes to the console for preview,
 *          see {@link IncrementalReleaseNotes.PreviewTask}</li>
 * </ul>
 */
public class ReleaseNotesPlugin implements Plugin<Project> {

    private static final String FETCH_NOTES_TASK = "fetchReleaseNotes";
    public static final String UPDATE_NOTES_TASK = "updateReleaseNotes";
    private static final String PREVIEW_NOTES_TASK = "previewReleaseNotes";

    public void apply(final Project project) {
        final ReleaseConfiguration conf = project.getPlugins().apply(ReleaseConfigurationPlugin.class).getConfiguration();
        project.getPlugins().apply(VersioningPlugin.class);
        project.getPlugins().apply(ContributorsPlugin.class);

        releaseNotesTasks(project, conf);
    }

    private static void releaseNotesTasks(final Project project, final ReleaseConfiguration conf) {
        final ReleaseNotesFetcherTask fetcher = TaskMaker.task(project, FETCH_NOTES_TASK, ReleaseNotesFetcherTask.class, new Action<ReleaseNotesFetcherTask>() {
            public void execute(final ReleaseNotesFetcherTask t) {
                t.setDescription("Fetches release notes data from Git and GitHub and serializes them to a file");
                t.setOutputFile(new File(project.getBuildDir(), "detailed-release-notes.ser"));

                deferredConfiguration(project, new Runnable() {
                    public void run() {
                        t.setGitHubReadOnlyAuthToken(conf.getGitHub().getReadOnlyAuthToken());
                        t.setGitHubRepository(conf.getGitHub().getRepository());
                        t.setPreviousVersion(conf.getPreviousReleaseVersion());
                    }
                });
            }
        });

        TaskMaker.task(project, UPDATE_NOTES_TASK, IncrementalReleaseNotes.UpdateTask.class, new Action<IncrementalReleaseNotes.UpdateTask>() {
            public void execute(final IncrementalReleaseNotes.UpdateTask t) {
                t.setDescription("Updates release notes file.");
                configureDetailedNotes(t, fetcher, project, conf);
            }
        });

        TaskMaker.task(project, PREVIEW_NOTES_TASK, IncrementalReleaseNotes.PreviewTask.class, new Action<IncrementalReleaseNotes.PreviewTask>() {
            public void execute(final IncrementalReleaseNotes.PreviewTask t) {
                t.setDescription("Shows new incremental content of release notes. Useful for previewing the release notes.");
                configureDetailedNotes(t, fetcher, project, conf);
            }
        });
    }

    private static void configureDetailedNotes(final IncrementalReleaseNotes task, final ReleaseNotesFetcherTask fetcher,
                                               final Project project, final ReleaseConfiguration conf) {
        task.dependsOn(fetcher, ContributorsPlugin.CONFIGURE_CONTRIBUTORS_TASK);
        deferredConfiguration(project, new Runnable() {
            public void run() {
                task.setReleaseNotesData(fetcher.getOutputFile());
                task.setDevelopers(conf.getTeam().getDevelopers());
                task.setContributors(conf.getTeam().getContributors());
                task.setGitHubLabelMapping(conf.getReleaseNotes().getLabelMapping()); //TODO make it optional
                task.setReleaseNotesFile(project.file(conf.getReleaseNotes().getFile())); //TODO add sensible default
                task.setGitHubRepository(conf.getGitHub().getRepository());
                task.setPreviousVersion(project.getExtensions().getByType(VersionInfo.class).getPreviousVersion());
            }
        });
    }
}
