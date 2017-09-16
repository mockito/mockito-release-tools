package org.shipkit.internal.gradle.versionupgrade

import org.shipkit.gradle.exec.ShipkitExecTask
import org.shipkit.gradle.git.GitPushTask
import org.shipkit.internal.gradle.git.GitAuthPlugin
import org.shipkit.internal.gradle.git.tasks.GitCheckOutTask
import org.shipkit.internal.gradle.git.tasks.GitPullTask
import testutil.PluginSpecification

import static org.shipkit.internal.gradle.versionupgrade.UpgradeDependencyPlugin.CREATE_PULL_REQUEST
import static org.shipkit.internal.gradle.versionupgrade.UpgradeDependencyPlugin.PULL_UPSTREAM
import static org.shipkit.internal.gradle.versionupgrade.UpgradeDependencyPlugin.PUSH_VERSION_UPGRADE

class UpgradeDependencyPluginTest extends PluginSpecification {

    def setup() {
        conf.gitHub.writeAuthToken = "secret"
    }

    def "should initialize plugin correctly and with default values"() {
        when:
        def versionUpgrade = project.plugins.apply(UpgradeDependencyPlugin).upgradeDependencyExtension

        then:
        versionUpgrade.buildFile == project.file("build.gradle")
        versionUpgrade.baseBranch == "master"

        project.tasks.checkoutBaseBranch
        project.tasks.checkoutVersionBranch
        project.tasks.replaceVersion
        project.tasks.commitVersionUpgrade
        project.tasks.pushVersionUpgrade
        project.tasks.createPullRequest
        project.tasks.performVersionUpgrade
    }

    def "should configure VersionUpgrade extension basing on dependencyNewVersion parameter"() {
        given:
        project.extensions.dependency = "org.shipkit:shipkit:0.1.2"

        when:
        def versionUpgrade = project.plugins.apply(UpgradeDependencyPlugin).upgradeDependencyExtension

        then:
        versionUpgrade.dependencyGroup == "org.shipkit"
        versionUpgrade.dependencyName == "shipkit"
        versionUpgrade.newVersion == "0.1.2"
    }

    def "should configure checkoutBaseBranch"() {
        when:
        def versionUpgrade = project.plugins.apply(UpgradeDependencyPlugin).upgradeDependencyExtension
        versionUpgrade.baseBranch = "release/2.x"

        project.evaluate()
        GitCheckOutTask task = project.tasks.checkoutBaseBranch

        then:
        task.rev == "release/2.x"
        task.newBranch == false
    }

    def "should configure pullUpstream"() {
        given:
        conf.gitHub.url = "http://github.com"
        conf.gitHub.repository = "mockito/shipkit"
        conf.dryRun = true
        conf.gitHub.writeAuthToken = "writeToken"
        conf.gitHub.writeAuthUser = "writeUser"

        when:
        def versionUpgrade = project.plugins.apply(UpgradeDependencyPlugin).upgradeDependencyExtension
        versionUpgrade.baseBranch = "release/2.x"

        project.evaluate()
        GitPullTask task = project.tasks.pullUpstream

        then:
        //task.url == "https://writeUser:writeToken@github.com/mockito/shipkit.git"
        task.rev == "release/2.x"
        task.dryRun
    }

    def "configures tasks based on git auth"() {
        conf.gitHub.repository = 'my-org/my-repo'
        conf.gitHub.writeAuthToken = 'foo'

        when:
        project.plugins.apply(UpgradeDependencyPlugin)
        project.tasks[GitAuthPlugin.IDENTIFY_GIT_ORIGIN_TASK].execute()

        then:
        GitPullTask pull = project.tasks[PULL_UPSTREAM]
        pull.secretValue == 'foo'
        pull.url == 'https://dummy:foo@github.com/my-org/my-repo.git'

        GitPushTask push = project.tasks[PUSH_VERSION_UPGRADE]
        push.secretValue == 'foo'
        push.url == 'https://dummy:foo@github.com/my-org/my-repo.git'

        CreatePullRequestTask pr = project.tasks[CREATE_PULL_REQUEST]
        pr.forkRepositoryName == 'my-org/my-repo'
        pr.upstreamRepositoryName == 'mockito/my-repo'
    }

    def "should configure checkoutVersionBranch"() {
        when:
        project.extensions.dependency = "org.shipkit:shipkit:0.1.2"
        project.plugins.apply(UpgradeDependencyPlugin)

        GitCheckOutTask task = project.tasks.checkoutVersionBranch

        then:
        task.rev == "upgrade-shipkit-to-0.1.2"
        task.newBranch == true
    }

    def "should configure replaceVersion"() {
        given:
        project.extensions.dependency = "org.shipkit:shipkit:0.1.2"

        when:
        UpgradeDependencyExtension versionUpgrade = project.plugins.apply(UpgradeDependencyPlugin).upgradeDependencyExtension
        ReplaceVersionTask task = project.tasks.replaceVersion

        then:
        task.versionUpgrade == versionUpgrade
    }

    def "should configure gitCommitVersionUpgrade"() {
        given:
        def dependencyFile = tmp.newFile("gradle.properties")
        project.extensions.dependency = "org.shipkit:shipkit:1.2.30"

        when:
        def versionUpgrade = project.plugins.apply(UpgradeDependencyPlugin).upgradeDependencyExtension
        versionUpgrade.buildFile = dependencyFile
        project.evaluate()

        ShipkitExecTask task = project.tasks.commitVersionUpgrade

        then:
        task.execCommands[0].commandLine ==
            ["git", "commit", "-m", "shipkit version upgraded to 1.2.30", dependencyFile.absolutePath]
    }

    def "should configure pushVersionUpgrade"() {
        given:
        project.extensions.dependency = "org.shipkit:shipkit:1.2.30"

        when:
        project.plugins.apply(UpgradeDependencyPlugin)

        GitPushTask task = project.tasks.pushVersionUpgrade

        then:
        task.targets == ["upgrade-shipkit-to-1.2.30"]
    }

    def "should configure createPullRequest"() {
        given:
        project.extensions.dependency = "org.shipkit:shipkit:1.2.30"
        conf.gitHub.apiUrl = "http://api.com"
        conf.gitHub.repository = "mockito/mockito"
        conf.gitHub.writeAuthToken = "writeToken"

        when:
        def versionUpgrade = project.plugins.apply(UpgradeDependencyPlugin).upgradeDependencyExtension
        CreatePullRequestTask task = project.tasks.createPullRequest

        then:
        task.gitHubApiUrl == "http://api.com"
        task.authToken == "writeToken"
        task.versionUpgrade == versionUpgrade
        task.versionBranch == "upgrade-shipkit-to-1.2.30"
    }
}
