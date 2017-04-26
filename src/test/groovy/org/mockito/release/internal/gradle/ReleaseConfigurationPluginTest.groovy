package org.mockito.release.internal.gradle

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class ReleaseConfigurationPluginTest extends Specification {

    def root = new ProjectBuilder().build()
    def subproject = new ProjectBuilder().withParent(root).build()

    def "singleton configuration, root applied first"() {
        expect:
        root.plugins.apply(ReleaseConfigurationPlugin).configuration == subproject.plugins.apply(ReleaseConfigurationPlugin).configuration
    }

    def "singleton configuration, subproject applied first"() {
        expect:
        subproject.plugins.apply(ReleaseConfigurationPlugin).configuration == root.plugins.apply(ReleaseConfigurationPlugin).configuration
    }

    def "dry run off by default"() {
        expect:
        !root.plugins.apply(ReleaseConfigurationPlugin).configuration.dryRun
    }

    def "configures dry run by project property"() {
        root.ext.releaseDryRun = ""
        expect:
        root.plugins.apply(ReleaseConfigurationPlugin).configuration.dryRun
    }

    def "should set team.addContributorsToPomFromGitHub to true by default"() {
        expect:
        root.plugins.apply(ReleaseConfigurationPlugin).configuration.team.addContributorsToPomFromGitHub == true
    }

    def "should team.addContributorsToPomFromGitHub be false when set to false"() {
        when:
        def configuration = root.plugins.apply(ReleaseConfigurationPlugin).configuration
        configuration.team.addContributorsToPomFromGitHub = false

        then:
        configuration.team.addContributorsToPomFromGitHub == false
    }


}
