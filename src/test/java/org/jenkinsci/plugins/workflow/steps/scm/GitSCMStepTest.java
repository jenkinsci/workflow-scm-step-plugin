/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.steps.scm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.io.FileMatchers.anExistingDirectory;

import hudson.model.Label;
import hudson.scm.ChangeLogSet;
import hudson.triggers.SCMTrigger;

import jenkins.plugins.git.GitSampleRepoRule;

import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class GitSCMStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule rr = new RestartableJenkinsRule();
    @Rule public GitSampleRepoRule sampleGitRepo = new GitSampleRepoRule();

    @Issue("JENKINS-26761")
    @Test
    public void checkoutsRestored() throws Exception {
        rr.then(
                r -> {
                    sampleGitRepo.init();
                    WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
                    p.addTrigger(new SCMTrigger(""));
                    r.createOnlineSlave(Label.get("remote"));
                    p.setDefinition(
                            new CpsFlowDefinition(
                                    "node('remote') {\n"
                                            + "    ws {\n"
                                            + "        git($/"
                                            + sampleGitRepo
                                            + "/$)\n"
                                            + "    }\n"
                                            + "}",
                                    true));
                    p.save();
                    WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                    r.assertLogContains("Cloning the remote Git repository", b);
                    FileUtils.copyFile(new File(b.getRootDir(), "build.xml"), System.out);
                });
        rr.then(
                r -> {
                    WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
                    r.createOnlineSlave(Label.get("remote"));
                    sampleGitRepo.write("nextfile", "");
                    sampleGitRepo.git("add", "nextfile");
                    sampleGitRepo.git("commit", "--message=next");
                    sampleGitRepo.notifyCommit(r);
                    WorkflowRun b = p.getLastBuild();
                    assertEquals(2, b.number);
                    r.assertLogContains(
                            "Cloning the remote Git repository", b); // new agent, new workspace
                    List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
                    assertEquals(1, changeSets.size());
                    ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
                    assertEquals(b, changeSet.getRun());
                    assertEquals("git", changeSet.getKind());
                    Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
                    assertTrue(iterator.hasNext());
                    ChangeLogSet.Entry entry = iterator.next();
                    assertEquals("[nextfile]", entry.getAffectedPaths().toString());
                    assertFalse(iterator.hasNext());
                });
    }

    @Test
    public void gitChangelogSmokes() {
        rr.then(
                r -> {
                    sampleGitRepo.init();
                    String repo = sampleGitRepo.fileUrl();
                    URI repoURI = new URI(repo);
                    assertThat("Repo: " + repo, new File(repoURI), is(anExistingDirectory()));
                    File gitDir = new File(new File(repoURI), ".git");
                    assertThat("gitDir: " + repo + "/.git", gitDir, is(anExistingDirectory()));
                    WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
                    p.setDefinition(
                            new CpsFlowDefinition(
                                    "node() {\n"
                                            + "  checkout(scm: [\n"
                                            + "    $class: 'GitSCM',\n"
                                            + "    branches: [[name: '*/master']],\n"
                                            + "    userRemoteConfigs: [[url: '"
                                            + repo
                                            + "']]\n"
                                            + "  ])\n"
                                            + "}",
                                    true));
                    sampleGitRepo.write("foo", "bar");
                    sampleGitRepo.git("add", "foo");
                    sampleGitRepo.git("commit", "-m", "Initial commit");
                    WorkflowRun b1 = r.buildAndAssertSuccess(p);
                    assertThat(b1.getCulpritIds(), Matchers.equalTo(Collections.emptySet()));
                    sampleGitRepo.write("foo", "bar1");
                    sampleGitRepo.git("add", "foo");
                    sampleGitRepo.git("commit", "-m", "Second commit");
                    WorkflowRun b2 = r.buildAndAssertSuccess(p);
                    assertThat(b2.getCulpritIds(), Matchers.equalTo(Collections.singleton("gits")));
                });
    }
}
