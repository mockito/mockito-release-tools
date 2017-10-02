package org.shipkit.internal.gradle.release;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.shipkit.gradle.configuration.ShipkitConfiguration;
import org.shipkit.gradle.notes.UpdateReleaseNotesTask;
import org.shipkit.internal.gradle.configuration.ShipkitConfigurationPlugin;
import org.shipkit.internal.gradle.git.GitPlugin;
import org.shipkit.internal.gradle.notes.ReleaseNotesPlugin;
import org.shipkit.internal.gradle.plugin.GradlePortalPublishPlugin;
import org.shipkit.internal.gradle.plugin.PluginDiscoveryPlugin;
import org.shipkit.internal.gradle.plugin.PluginValidationPlugin;

/**
 * Automated releases to Gradle Plugin portal.
 * Sets up task dependencies and applies configuration needed for automated releases of Gradle plugins.
 * Intended to be applied to the root project.
 *
 * Applies:
 * <ul>
 *     <li>{@link ReleasePlugin}</li>
 * </ul>
 *
 * Applies to every subproject with "com.gradle.plugin-publish" plugin:
 * <ul>
 *     <li>{@link GradlePortalPublishPlugin}</li>
 *     <li>{@link PluginDiscoveryPlugin}</li>
 *     <li>{@link PluginValidationPlugin}</li>
 * </ul>
 *
 * Behavior:
 * <ul>
 *     <li>Hooks up task dependencies so that performing release will publish all plugins from all subprojects</li>
 * </ul>
 */
public class GradlePortalReleasePlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPlugins().apply(ReleasePlugin.class);
        final ShipkitConfiguration conf = project.getPlugins().apply(ShipkitConfigurationPlugin.class).getConfiguration();
        final Task performRelease = project.getTasks().getByName(ReleasePlugin.PERFORM_RELEASE_TASK);
        final Task gitPush = project.getTasks().getByName(GitPlugin.GIT_PUSH_TASK);

        project.allprojects(new Action<Project>() {
            @Override
            public void execute(final Project subproject) {
                subproject.getPlugins().withId("com.gradle.plugin-publish", new Action<Plugin>() {
                    @Override
                    public void execute(Plugin plugin) {
                        subproject.getPlugins().apply(PluginDiscoveryPlugin.class);
                        subproject.getPlugins().apply(PluginValidationPlugin.class);
                        subproject.getPlugins().apply(GradlePortalPublishPlugin.class);

                        Task publishPlugins = subproject.getTasks().getByName(GradlePortalPublishPlugin.PUBLISH_PLUGINS_TASK);

                        performRelease.dependsOn(publishPlugins); //perform release will actually publish the plugins
                        publishPlugins.mustRunAfter(gitPush);     //git push is easier to revert than perform release

                        //We first build plugins to be published, then do git push, we're using 'buildArchives' for that
                        //We know that "buildArchives" task exists because 'com.gradle.plugin-publish' applies Java plugin
                        Task archivesTask = subproject.getTasks().getByName("buildArchives");
                        publishPlugins.dependsOn(archivesTask);
                        gitPush.mustRunAfter(archivesTask);

                        UpdateReleaseNotesTask updateNotes = (UpdateReleaseNotesTask) project.getTasks().getByName(ReleaseNotesPlugin.UPDATE_NOTES_TASK);
                        updateNotes.setPublicationRepository(conf.getReleaseNotes().getPublicationRepository());
                    }
                });
            }
        });
    }
}
