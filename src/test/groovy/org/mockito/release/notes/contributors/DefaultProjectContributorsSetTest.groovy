package org.mockito.release.notes.contributors

import spock.lang.Specification

class DefaultProjectContributorsSetTest extends Specification {

    def set = new DefaultProjectContributorsSet()

    def "does not replace existing contributor"() {
        set.addContributor(new DefaultProjectContributor("a", "a", "a", 2000))
        //this is important use case because of how we get contributors from GitHub.
        // We issue 2 queries to GitHub, first query gets us most contributors, second gets us most recent contributors
        // Same contributor with just one contribution:
        set.addContributor(new DefaultProjectContributor("a", "a", "a", 1))

        expect:
        def c = set.allContributors as List
        c.size() == 1
        c[0].numberOfContributions == 2000
    }

    def "does not replace existing contributors and ensures sort order"() {
        set.addAllContributors([
            new DefaultProjectContributor("a", "a", "a", 10),
            new DefaultProjectContributor("b", "b", "b", 10)
        ])
        set.addAllContributors([
            new DefaultProjectContributor("a", "a", "a", 1),
            new DefaultProjectContributor("c", "c", "c", 1)
        ])

        expect:
        set.allContributors.toString() == "[b/b[10], a/a[10], c/c[1]]"
    }

    def "does not drop contributors with the same amount of contributions"() {
        set.addContributor(new DefaultProjectContributor(
                "Szczepan Faber 1", "szczepiq", "http://github.com/szczepiq", 2000))
        set.addContributor(new DefaultProjectContributor(
                "Szczepan Faber 2", "szczepiq", "http://github.com/szczepiq", 2000))

        expect:
        set.allContributors.size() == 2
    }

    def "finds by name"() {
        set.addAllContributors([
                new DefaultProjectContributor("a", "a", "a", 10),
                new DefaultProjectContributor("b", "b", "b", 10)
        ])

        expect:
        set.findByName("c") == null
        set.findByName("b").name == "b"
        set.findByName("a").name == "a"
    }

    def "empty to config notation"() {
        expect:
        set.toConfigNotation() == []
    }

    def "two contributors to config notation"() {
        set.addAllContributors([
                new DefaultProjectContributor("aa", "a", "a", 10),
                new DefaultProjectContributor("bb", "b", "b", 5)
        ])

        expect:
        set.toConfigNotation() == ["a:aa", "b:bb"]
    }

    def "empty GitHub name to config notation"() {
        set.addAllContributors([
                new DefaultProjectContributor("", "login", "a", 10),
        ])

        expect:
        set.toConfigNotation() == ["login:login"]
    }
}
