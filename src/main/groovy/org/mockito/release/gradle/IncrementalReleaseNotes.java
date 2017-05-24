package org.mockito.release.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;
import org.mockito.release.internal.gradle.util.FileUtil;
import org.mockito.release.internal.gradle.util.ReleaseNotesSerializer;
import org.mockito.release.internal.gradle.util.team.TeamMember;
import org.mockito.release.internal.gradle.util.team.TeamParser;
import org.mockito.release.notes.contributors.AllContributorsSerializer;
import org.mockito.release.notes.contributors.DefaultContributor;
import org.mockito.release.notes.contributors.DefaultProjectContributorsSet;
import org.mockito.release.notes.contributors.ProjectContributorsSet;
import org.mockito.release.notes.format.ReleaseNotesFormatters;
import org.mockito.release.notes.model.Contributor;
import org.mockito.release.notes.model.ProjectContributor;
import org.mockito.release.notes.model.ReleaseNotesData;
import org.mockito.release.notes.util.IOUtil;

import java.io.File;
import java.util.*;

/**
 * Generates incremental, detailed release notes text.
 * that can be appended to the release notes file.
 */
public abstract class IncrementalReleaseNotes extends DefaultTask {

    private static final Logger LOG = Logging.getLogger(IncrementalReleaseNotes.class);

    private String previousVersion;
    private File releaseNotesFile;
    private String gitHubRepository;
    private Map<String, String> gitHubLabelMapping = new LinkedHashMap<String, String>();
    private String publicationRepository;
    private File releaseNotesData;
    private Collection<String> developers;
    private Collection<String> contributors;
    private File contributorsDataFile;

    /**
     * Release notes file this task operates on.
     */
    @InputFile
    public File getReleaseNotesFile() {
        return releaseNotesFile;
    }

    /**
     * See {@link #getReleaseNotesFile()}
     */
    public void setReleaseNotesFile(File releaseNotesFile) {
        this.releaseNotesFile = releaseNotesFile;
    }

    /**
     * Name of the GitHub repository in format "user|org/repository",
     * for example: "mockito/mockito"
     */
    @Input
    public String getGitHubRepository() {
        return gitHubRepository;
    }

    /**
     * See {@link #getGitHubRepository()}
     */
    public void setGitHubRepository(String gitHubRepository) {
        this.gitHubRepository = gitHubRepository;
    }

    /**
     * Issue tracker label mappings.
     * The mapping of "GitHub label" to human readable and presentable name.
     * The order of labels is important and will influence the order
     * in which groups of issues are generated in release notes.
     * Examples: ['java-9': 'Java 9 support', 'BDD': 'Behavior-Driven Development support']
     */
    @Input
    @Optional
    public Map<String, String> getGitHubLabelMapping() {
        return gitHubLabelMapping;
    }

    /**
     * See {@link #getGitHubLabelMapping()}
     */
    public void setGitHubLabelMapping(Map<String, String> gitHubLabelMapping) {
        this.gitHubLabelMapping = gitHubLabelMapping;
    }

    /**
     * The target repository where the publications / binaries are published to.
     * Shown in the release notes.
     */
    @Input
    public String getPublicationRepository() {
        return publicationRepository;
    }

    /**
     * See {@link #getPublicationRepository()}
     */
    public void setPublicationRepository(String publicationRepository) {
        this.publicationRepository = publicationRepository;
    }

    /**
     * Previous released version we generate the release notes from.
     */
    @Input
    public String getPreviousVersion() {
        return previousVersion;
    }

    /**
     * See {@link #getPreviousVersion()}
     */
    public void setPreviousVersion(String previousVersion) {
        this.previousVersion = previousVersion;
    }

    /**
     * Input to the release notes generation,
     * serialized release notes data objects of type {@link ReleaseNotesData}.
     * They are used to generate formatted release notes.
     */
    @InputFile
    public File getReleaseNotesData() {
        return releaseNotesData;
    }

    /**
     * See {@link #getReleaseNotesData()}
     */
    public void setReleaseNotesData(File releaseNotesData) {
        this.releaseNotesData = releaseNotesData;
    }

    /**
     * Developers as configured in {@link ReleaseConfiguration.Team#getDevelopers()}
     */
    @Input public Collection<String> getDevelopers() {
        return developers;
    }

    /**
     * See {@link #getDevelopers()}
     */
    public void setDevelopers(Collection<String> developers) {
        this.developers = developers;
    }

    /**
     * Contributors as configured in {@link ReleaseConfiguration.Team#getContributors()}
     */
    @Input public Collection<String> getContributors() {
        return contributors;
    }

    /**
     * See {@link #getContributors()}
     */
    public void setContributors(Collection<String> contributors) {
        this.contributors = contributors;
    }

    /**
     * {@link #getContributorsDataFile()}
     */

    public void setContributorsDataFile(File contributorsDataFile) {
        this.contributorsDataFile = contributorsDataFile;
    }

    /**
     * File name from reads contributors from GitHub
     */
    @InputFile public File getContributorsDataFile() {
        return contributorsDataFile;
    }

    private void assertConfigured() {
        //TODO SF unit test coverage
        if (releaseNotesFile == null || !releaseNotesFile.isFile()) {
            throw new GradleException("'" + this.getPath() + ".releaseNotesFile' must be configured and the file must be present.");
        }

        if (gitHubRepository == null || gitHubRepository.trim().isEmpty()) {
            throw new GradleException("'" + this.getPath() + "gitHubRepository' must be configured.");
        }
    }

    /**
     * Generates new incremental content of the release notes.
     */
    String getNewContent() {
        assertConfigured();
        LOG.lifecycle("  Building new release notes based on {}", releaseNotesFile);

        String version = getProject().getVersion().toString();
        String tagPrefix = "v";

        Collection<ReleaseNotesData> data = new ReleaseNotesSerializer().deserialize(IOUtil.readFully(releaseNotesData));

        String vcsCommitTemplate = "https://github.com/" + gitHubRepository + "/compare/"
                + tagPrefix + previousVersion + "..." + tagPrefix + version;

        ProjectContributorsSet contributorsFromGitHub;
        if(!contributors.isEmpty()) {
            // if contributors are defined in releasing.team.contributors don't deserialize them from file
            contributorsFromGitHub = new DefaultProjectContributorsSet();
        } else {
            LOG.info("  Read project contributors from file " + contributorsDataFile.getAbsolutePath());
            contributorsFromGitHub = new AllContributorsSerializer().deserialize(IOUtil.readFully(contributorsDataFile));
        }

        Map<String, Contributor> contributorsMap = contributorsMap(contributors, contributorsFromGitHub, developers);
        String notes = ReleaseNotesFormatters.detailedFormatter(
                "", gitHubLabelMapping, vcsCommitTemplate, publicationRepository, contributorsMap)
                .formatReleaseNotes(data);

        return notes + "\n\n";
    }

    //TODO SF deduplicate and unit test
    static Map<String, Contributor> contributorsMap(Collection<String> contributorsFromConfiguration,
                                                    ProjectContributorsSet contributorsFromGitHub,
                                                    Collection<String> developers) {
        Map<String, Contributor> out = new HashMap<String, Contributor>();
        for (String contributor : contributorsFromConfiguration) {
            TeamMember member = TeamParser.parsePerson(contributor);
            out.put(member.name, new DefaultContributor(member.name, member.gitHubUser,
                    "http://github.com/" + member.gitHubUser));
        }
        for (ProjectContributor projectContributor : contributorsFromGitHub.getAllContributors()) {
            out.put(projectContributor.getName(), projectContributor);
        }
        for (String developer : developers) {
            TeamMember member = TeamParser.parsePerson(developer);
            out.put(member.name, new DefaultContributor(member.name, member.gitHubUser,
                    "http://github.com/" + member.gitHubUser));
        }
        return out;
    }

    /**
     * Generates incremental, detailed release notes text
     * and appends it to the top of the release notes file.
     */
    public static class UpdateTask extends IncrementalReleaseNotes {

        /**
         * Delegates to {@link IncrementalReleaseNotes#getReleaseNotesFile()}.
         * Configured here only to specify Gradle's output file and make the task incremental.
         */
        @OutputFile
        public File getReleaseNotesFile() {
            return super.getReleaseNotesFile();
        }

        @TaskAction public void updateReleaseNotes() {
            String newContent = super.getNewContent();
            FileUtil.appendToTop(newContent, getReleaseNotesFile());
            LOG.lifecycle("  Successfully updated release notes!");
        }
    }

    /**
     * Generates incremental, detailed release notes text
     * and appends it to the top of the release notes file.
     */
    public static class PreviewTask extends IncrementalReleaseNotes {
        @TaskAction public void updateReleaseNotes() {
            String newContent = super.getNewContent();
            LOG.lifecycle("----------------\n" + newContent + "----------------");
        }
    }
}
