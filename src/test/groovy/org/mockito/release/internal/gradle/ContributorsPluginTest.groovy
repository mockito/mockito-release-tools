package org.mockito.release.internal.gradle

import testutil.PluginSpecification

class ContributorsPluginTest extends PluginSpecification {

    def "applies"() {
        expect:
        project.plugins.apply("org.mockito.release-tools.contributors")
    }
}
