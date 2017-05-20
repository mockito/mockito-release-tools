package org.mockito.release.notes.vcs;

import org.mockito.release.notes.model.Commit;
import org.mockito.release.notes.util.Predicate;

public class IgnoreCommitByConfigMessagePostfix implements Predicate<Commit> {
    private final String postfix;

    public IgnoreCommitByConfigMessagePostfix(String postfix) {
        this.postfix = postfix;
    }

    @Override
    public boolean isTrue(Commit commit) {
        final int expectedPostfixIndex = commit.getMessage().length() - postfix.length();
        return commit.getMessage().lastIndexOf(postfix) == expectedPostfixIndex;
    }
}
