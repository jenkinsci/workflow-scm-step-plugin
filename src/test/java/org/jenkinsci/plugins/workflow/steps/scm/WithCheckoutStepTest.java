/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import hudson.model.Label;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.triggers.SCMTrigger;
import hudson.util.StreamTaskListener;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.subversion.SubversionSampleRepoRule;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WithCheckoutStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule r = new RestartableJenkinsRule();
    @Rule public GitSampleRepoRule sampleGitRepo = new GitSampleRepoRule();
    @Rule public GitSampleRepoRule nestedGitRepo = new GitSampleRepoRule();
    @Rule public SubversionSampleRepoRule sampleSvnRepo = new SubversionSampleRepoRule();

    @Issue("JENKINS-26100")
    @Test
    public void scmVars() throws Exception {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleGitRepo.init();
                sampleGitRepo.write("Jenkinsfile", "node('remote') {\n" +
                        "    withCheckout(scm) {\n" +
                        "        echo \"GIT_COMMIT is ${env.GIT_COMMIT}\"\n" +
                        "    }\n" +
                        "}\n");
                sampleGitRepo.git("add", "Jenkinsfile");
                sampleGitRepo.git("commit", "--message=files");
                String commitHash = sampleGitRepo.head();
                WorkflowJob p = r.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsScmFlowDefinition(new jenkins.plugins.git.GitStep(sampleGitRepo.toString()).createSCM(), "Jenkinsfile"));

                r.j.createOnlineSlave(Label.get("remote"));
                WorkflowRun b = r.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.j.assertLogContains("GIT_COMMIT is " + commitHash, b);
            }
        });
    }

    @Issue("JENKINS-26100")
    @Test
    public void nested() throws Exception {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                nestedGitRepo.init();
                nestedGitRepo.write("some-file", "Some text");
                nestedGitRepo.git("add", "some-file");
                nestedGitRepo.git("commit", "--message=files");

                sampleGitRepo.init();
                sampleGitRepo.write("Jenkinsfile", "node('remote') {\n" +
                        "    withCheckout(scm) {\n" +
                        "        withCheckout([$class: 'GitSCM', userRemoteConfigs: [[url: '" + nestedGitRepo.toString() + "']]]) {\n" +
                        "            echo \"GIT_COMMIT is ${env.GIT_COMMIT}\"\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
                sampleGitRepo.git("add", "Jenkinsfile");
                sampleGitRepo.git("commit", "--message=files");

                String commitHash = nestedGitRepo.head();
                WorkflowJob p = r.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsScmFlowDefinition(new jenkins.plugins.git.GitStep(sampleGitRepo.toString()).createSCM(), "Jenkinsfile"));

                r.j.createOnlineSlave(Label.get("remote"));
                WorkflowRun b = r.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.j.assertLogContains("GIT_COMMIT is " + commitHash, b);
            }
        });
    }

    @Issue("JENKINS-26761")
    @Test public void checkoutsRestored() throws Exception {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleGitRepo.init();
                WorkflowJob p = r.j.jenkins.createProject(WorkflowJob.class, "p");
                p.addTrigger(new SCMTrigger(""));
                r.j.createOnlineSlave(Label.get("remote"));
                sampleGitRepo.write("Jenkinsfile", "node('remote') {\n" +
                        "    ws {\n" +
                        "        withCheckout(scm) {\n" +
                        "            echo \"GIT_COMMIT is ${env.GIT_COMMIT}\"\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
                sampleGitRepo.git("add", "Jenkinsfile");
                sampleGitRepo.git("commit", "--message=files");
                p.setDefinition(new CpsScmFlowDefinition(new jenkins.plugins.git.GitStep(sampleGitRepo.toString()).createSCM(), "Jenkinsfile"));
                p.save();
                WorkflowRun b = r.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.j.assertLogContains("Cloning the remote Git repository", b);
                String commitHash = sampleGitRepo.head();
                r.j.assertLogContains("GIT_COMMIT is " + commitHash, b);
                FileUtils.copyFile(new File(b.getRootDir(), "build.xml"), System.out);
            }
        });
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = r.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                r.j.createOnlineSlave(Label.get("remote"));
                sampleGitRepo.write("nextfile", "");
                sampleGitRepo.git("add", "nextfile");
                sampleGitRepo.git("commit", "--message=next");
                sampleGitRepo.notifyCommit(r.j);
                WorkflowRun b = p.getLastBuild();
                assertEquals(2, b.number);
                r.j.assertLogContains("Cloning the remote Git repository", b); // new slave, new workspace
                String commitHash = sampleGitRepo.head();
                r.j.assertLogContains("GIT_COMMIT is " + commitHash, b);
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
            }
        });
    }

    @Issue("JENKINS-32214")
    @Test public void pollDuringBuild() throws Exception {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleSvnRepo.init();
                sampleSvnRepo.write("Jenkinsfile", "semaphore 'before'\n" +
                        "node {\n" +
                        "    withCheckout(scm) {\n" +
                        "        echo \"SVN_REVISION is ${env.SVN_REVISION}\"\n" +
                        "    }\n" +
                        "}\n" +
                        "semaphore 'after'\n");
                sampleSvnRepo.svnkit("add", sampleSvnRepo.wc() + "/Jenkinsfile");
                sampleSvnRepo.svnkit("commit", "--message=+Jenkinsfile", sampleSvnRepo.wc());

                WorkflowJob p = r.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsScmFlowDefinition(new SubversionStep(sampleSvnRepo.trunkUrl()).createSCM(), "Jenkinsfile"));                 assertPolling(p, PollingResult.Change.INCOMPARABLE);
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.success("before/1", null);
                long firstRev = sampleSvnRepo.revision();
                SemaphoreStep.waitForStart("after/1", b1);
                r.j.assertLogContains("SVN_REVISION is " + firstRev, b1);
                assertPolling(p, PollingResult.Change.NONE);
                SemaphoreStep.success("after/1", null);
                r.j.assertBuildStatusSuccess(r.j.waitForCompletion(b1));
                sampleSvnRepo.write("file2", "");
                sampleSvnRepo.svnkit("add", sampleSvnRepo.wc() + "/file2");
                sampleSvnRepo.svnkit("commit", "--message=+file2", sampleSvnRepo.wc());
                long secondRev = sampleSvnRepo.revision();
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.success("before/2", null);
                SemaphoreStep.waitForStart("after/2", b2);
                r.j.assertLogContains("SVN_REVISION is " + secondRev, b2);
                assertPolling(p, PollingResult.Change.NONE);
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("before/3", b3);
                assertPolling(p, PollingResult.Change.NONE);
                sampleSvnRepo.write("file3", "");
                sampleSvnRepo.svnkit("add", sampleSvnRepo.wc() + "/file3");
                sampleSvnRepo.svnkit("commit", "--message=+file3", sampleSvnRepo.wc());
                assertPolling(p, PollingResult.Change.SIGNIFICANT);
            }
        });
    }
    private static void assertPolling(WorkflowJob p, PollingResult.Change expectedChange) {
        assertEquals(expectedChange, p.poll(StreamTaskListener.fromStdout()).change);
    }

}
