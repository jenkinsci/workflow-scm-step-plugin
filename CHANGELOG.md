## Changelog

### 2.11

Release date: 2020-04-17

-   Internal: Modify the empty changelog file deletion added in version 2.10 to
    only delete empty changelog files whose last modified date is unchanged from
    when the file was created so that FakeChangelogSCM works in tests. ([PR 40](https://github.com/jenkinsci/workflow-scm-step-plugin/pull/40))

### 2.10

Release date: 2020-01-22

-   Fix: When using the `checkout` step with `changelog: true` (the default), if 
    the SCM leaves the changelog file completely empty, delete it instead of
    passing the empty changelog file to other APIs. ([JENKINS-59560](https://issues.jenkins-ci.org/browse/JENKINS-59560), [JENKINS-57918](https://issues.jenkins-ci.org/browse/JENKINS-57918))

### 2.9

Release date: 2019-06-07

-   Fix: Use `rw-r--r--` (0644) permissions for changelog files on Posix
    systems as in version 2.7 and earlier of this plugin rather
    than `rw------- (0600)` as used in version 2.8 to avoid potential
    issues related to backups and similar scenarios. ([PR
    34](https://github.com/jenkinsci/workflow-scm-step-plugin/pull/34))

### 2.8

Release date: 2019-06-05

-   Fix: Prevent SAXParseException from being thrown when using
    the `checkout` step in parallel blocks.
    ([JENKINS-34313](https://issues.jenkins-ci.org/browse/JENKINS-34313))

### 2.7

Release date: 2018-10-01

-   Fix: Prevent ConcurrentModificationException when using
    the `checkout` step in parallel blocks.
    ([JENKINS-47201](https://issues.jenkins-ci.org/browse/JENKINS-47201))
-   Improvement: Change the display name of the `checkout` step from
    "General SCM" to "Check out from version control".
-   Improvement: Add documentation for specific variables returned by
    the `checkout` step based on the installed set of plugins.
    ([JENKINS-26100](https://issues.jenkins-ci.org/browse/JENKINS-26100))

### 2.6

Release date: 2017-06-20

-   [JENKINS-26100](https://issues.jenkins-ci.org/browse/JENKINS-26100) `checkout`
    step now returns a `Map` of environment variables set by the SCM in
    legacy Freestyle jobs, such as `GIT_COMMIT` and `SVN_REVISION`.

### 2.5

Release date: 2017-06-16

-   No user-facing changes.

### 2.4

Release date: 2017-02-27

-   No user-facing changes, only test infrastructure.

### 2.3

Release date: 2016-11-10

-   No user-facing changes, only internal metadata.

### 2.2

Release date: 2016-07-05

-   [JENKINS-35247](https://issues.jenkins-ci.org/browse/JENKINS-35247)
    `git` and `svn` steps now moved to their respective SCM plugins.
    This plugin now includes only the generic `checkout` step.

### 2.1

Release date: 2016-06-09

-   [JENKINS-35247](https://issues.jenkins-ci.org/browse/JENKINS-35247)
    Removed direct plugin dependencies on [Git
    Plugin](https://plugins.jenkins.io/git) and
    [Subversion
    Plugin](https://plugins.jenkins.io/subversion),
    in preparation for moving the `git` and `svn` steps to their natural
    homes. (forward-ported from 1.14.2 / 1.4.3)
-   Downgraded “overriding old revision state” warning.

### 2.0

Release date: 2016-04-05

-   First release under per-plugin versioning scheme. See [1.x
    changelog](https://github.com/jenkinsci/workflow-plugin/blob/82e7defa37c05c5f004f1ba01c93df61ea7868a5/CHANGES.md)
    for earlier releases.
